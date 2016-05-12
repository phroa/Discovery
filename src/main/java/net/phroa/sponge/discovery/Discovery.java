/**
 * Discovery
 *
 * Copyright (C) phroa <jack@phroa.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.phroa.sponge.discovery;

import com.flowpowered.math.vector.Vector3d;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.internal.util.logging.LogFactory;
import org.flywaydb.core.internal.util.logging.slf4j.Slf4jLog;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.entity.DisplaceEntityEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.pagination.PaginationList;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.title.Title;
import org.spongepowered.api.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

@Plugin(id = "discovery",
        name = "Discovery",
        version = "1.0.0",
        description = "Discoverable fast-travel targets",
        url = "https://github.com/phroa/Discovery")
public class Discovery {

    // Generic Sponge bits

    @Inject
    private Logger logger;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDir;
    @Inject
    @DefaultConfig(sharedRoot = false)
    private ConfigurationLoader<CommentedConfigurationNode> configurationLoader;
    private CommentedConfigurationNode rootNode;

    // Configuration options

    private Path databasePath;

    // SQL procedures

    /**
     * Fetch all regions.
     */
    private static final String ALL_REGIONS = "SELECT * FROM `regions`";

    /**
     * Create a region.
     */
    private static final String INSERT_REGION = "INSERT INTO `regions`\n"
            + "  (`uuid`, `name`, `world_uuid`, `x_min`, `z_min`, `x_max`, `z_max`, `teleport_x`, `teleport_y`, `teleport_z`, `creator`)\n"
            + "VALUES\n"
            + "  (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * Update a region by its UUID.
     */
    private static final String UPDATE_REGION = "UPDATE `regions`\n"
            + "SET\n"
            + "  `name` = ?,\n"
            + "  `world_uuid` = ?,\n"
            + "  `x_min` = ?,\n"
            + "  `x_max` = ?,\n"
            + "  `z_min` = ?,\n"
            + "  `z_max` = ?,\n"
            + "  `teleport_x` = ?,\n"
            + "  `teleport_y` = ?,\n"
            + "  `teleport_z` = ?,\n"
            + "  `creator` = ?\n"
            + "WHERE `uuid` = ?";

    /**
     * Delete a region by its UUID (at most 1 at a time).
     */
    private static final String DELETE_REGION = "DELETE FROM `regions`\n"
            + "  WHERE `uuid`=?";

    /**
     * Marks a region as discovered for a particular player.
     */
    private static final String DISCOVER_REGION = "INSERT INTO `discovered_regions`\n"
            + "  (`player_uuid`, `region_uuid`)\n"
            + "VALUES\n"
            + "  (?, ?)";

    /**
     * Fetch the regions that a player (whose UUID is a parameter to this statement) has discovered.
     */
    private static final String REGIONS_DISCOVERED_BY = "SELECT * FROM `regions`\n"
            + "  INNER JOIN `discovered_regions` ON `regions`.`uuid` = `discovered_regions`.`region_uuid`\n"
            + "WHERE `discovered_regions`.`player_uuid` = ?";

    // Caches

    // All regions. This isn't actually a Cache, so it needs to be managed manually.
    private SortedSet<Region> regions = new TreeSet<>(Region::compareTo);

    /**
     * Keeps track of the set of regions that a player has discovered, using their UUID as the key.
     *
     * Be sure to use {@link Map#computeIfAbsent(Object, Function)} with {@link #regionsFor(UUID)} instead of {@link Map#get(Object)}!
     */
    private Map<UUID, SortedSet<Region>> discoveredBy = Maps.newHashMap();

    @Listener
    public void onPreInitialization(GamePreInitializationEvent event) throws Exception {
        rootNode = configurationLoader.load();

        // The value "-1" would never be a real version number, so it's used as an indication that the config doesn't exist yet.
        int version = rootNode.getNode("version").getInt(-1);
        switch (version) {
            case -1:
                // Save the default and fall through to the next condition, which should be the latest version.
                rootNode = saveDefaultConfig();
                logger.info("Creating default configuration file in " + configDir);

            case 1:
                setDatabasePath(rootNode.getNode("database", "path").getString("discovery.db"));
                break;

            // Perhaps the plugin is outdated, or someone was messing with the version number
            default:
                logger.error("Unknown configuration file version \"" + version + "\", stopping...");
                Sponge.getServer().shutdown();
                return;
        }

        if (!Files.exists(databasePath)) {
            Files.createFile(databasePath);
        }

        // Pass our logger through to Flyway so that its messages are tied to the plugin ID
        LogFactory.setLogCreator(c -> new Slf4jLog(logger));

        // Run database schema migrations stored in "resources/db/migration"
        Flyway flyway = new Flyway();
        flyway.setDataSource("jdbc:sqlite:" + databasePath, null, null);
        flyway.migrate();

        // The server is starting up, so there shouldn't be any regions in the set. Flushing is OK.
        fetchAllRegions();
    }

    @Listener
    public void onInitialization(GameInitializationEvent event) {
        CommandSpec list = CommandSpec.builder()
                .description(Text.of("List discovered regions"))
                .permission("discovery.list")
                .executor((src, args) -> {
                    PaginationList.Builder page = PaginationList.builder()
                            .title(Text.of("Discovered Regions"));

                    if (src instanceof Player) {
                        // List only the regions the player can travel to
                        List<Text> regions = discoveredBy.computeIfAbsent(((Player) src).getUniqueId(), this::regionsFor)
                                .stream()
                                .map(Discovery::formatRegion)
                                .collect(Collectors.toList());

                        // Oddly enough, an empty paginator throws an error.
                        if (regions.size() > 0) {
                            page = page.contents(regions);
                        } else {
                            page = page.contents(Lists.newArrayList());
                        }
                    } else {
                        // src is console, so show all present in the cache
                        List<Text> regions = this.regions.stream()
                                .map(Discovery::formatRegion)
                                .collect(Collectors.toList());

                        // Oddly enough, an empty paginator throws an error.
                        if (regions.size() > 0) {
                            page = page.contents(regions);
                        } else {
                            page = page.contents(Lists.newArrayList());
                        }
                    }

                    page.sendTo(src);
                    return CommandResult.success();
                })
                .build();

        CommandSpec create = CommandSpec.builder()
                .description(Text.of("Create a new region"))
                .extendedDescription(Text.of("The order of arguments is:\nname x1 z1 x2 z2 teleportX teleportY teleportZ."))
                .permission("discovery.create")
                .arguments(GenericArguments.string(Text.of("name")),
                        GenericArguments.integer(Text.of("x1")),
                        GenericArguments.integer(Text.of("z1")),
                        GenericArguments.integer(Text.of("x2")),
                        GenericArguments.integer(Text.of("z2")),
                        GenericArguments.doubleNum(Text.of("teleportX")),
                        GenericArguments.doubleNum(Text.of("teleportY")),
                        GenericArguments.doubleNum(Text.of("teleportZ")))
                .executor((src, args) -> {
                    if (src instanceof Player) {
                        Player player = (Player) src;
                        UUID uuid = UUID.randomUUID();
                        String name = args.<String>getOne("name").get();
                        UUID worldUuid = player.getLocation().getExtent().getUniqueId();
                        int x1 = args.<Integer>getOne("x1").get();
                        int z1 = args.<Integer>getOne("z1").get();
                        int x2 = args.<Integer>getOne("x2").get();
                        int z2 = args.<Integer>getOne("z2").get();
                        double tx = args.<Double>getOne("teleportX").get();
                        double ty = args.<Double>getOne("teleportY").get();
                        double tz = args.<Double>getOne("teleportZ").get();
                        UUID creator = player.getUniqueId();

                        if (x2 < x1) {
                            int temp = x2;
                            x2 = x1;
                            x1 = temp;
                        }

                        if (z2 < z1) {
                            int temp = z2;
                            z2 = z1;
                            z1 = temp;
                        }

                        if (tx < x1 || tx > x2 || tz < z1 || tz > z2) {
                            src.sendMessage(Text.of(String.format("Teleport position (%.2f, %.2f, %.2f) is not inside the region.", tx, ty, tz)));
                            return CommandResult.empty();
                        }

                        regions.add(new Region(uuid, name, worldUuid, x1, z1, x2, z2, tx, ty, tz, creator));

                        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                                PreparedStatement statement = connection.prepareStatement(INSERT_REGION)) {
                            statement.setString(1, uuid.toString());
                            statement.setString(2, name);
                            statement.setString(3, worldUuid.toString());
                            statement.setInt(4, x1);
                            statement.setInt(5, z1);
                            statement.setInt(6, x2);
                            statement.setInt(7, z2);
                            statement.setDouble(8, tx);
                            statement.setDouble(9, ty);
                            statement.setDouble(10, tz);
                            statement.setString(11, creator.toString());

                            statement.executeUpdate();
                        } catch (SQLException e) {
                            throw new CommandException(Text.of("Database error"), e);
                        }
                        src.sendMessage(Text.of("Created " + name + "."));
                        return CommandResult.success();
                    } else {
                        src.sendMessage(Text.of("You need to be a player to use this."));
                        return CommandResult.empty();
                    }
                })
                .build();

        CommandSpec delete = CommandSpec.builder()
                .description(Text.of("Delete a region by its UUID"))
                .permission("discovery.delete")
                .arguments(GenericArguments.string(Text.of("uuid")))
                .executor((src, args) -> {
                    // Sure, we could use the region name and do a lookup.
                    // Using the UUID implies that someone's really serious about deleting a region.
                    String uuid = args.<String>getOne("uuid").get();

                    // If any rows were deleted, this would be at least (and hopefully at most!) 1
                    int deleted = 0;
                    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                            PreparedStatement statement = connection.prepareStatement(DELETE_REGION)) {
                        statement.setString(1, uuid);
                        deleted = statement.executeUpdate();
                    } catch (SQLException e) {
                        throw new CommandException(Text.of("Database error"), e);
                    }

                    if (deleted > 0) {
                        src.sendMessage(Text.of("Deleted region with UUID " + uuid + "."));
                        fetchAllRegions();
                        discoveredBy.clear();
                        return CommandResult.success();
                    } else {
                        src.sendMessage(Text.of("Problem deleting region with UUID " + uuid + "."));
                        return CommandResult.empty();
                    }
                })
                .build();

        CommandSpec rename = CommandSpec.builder()
                .description(Text.of("Rename a region"))
                .permission("discovery.rename")
                .arguments(GenericArguments.string(Text.of("from")), GenericArguments.string(Text.of("to")))
                .executor((src, args) -> {
                    String from = args.<String>getOne("from").get();
                    String to = args.<String>getOne("to").get();

                    Optional<Region> fromOptional = regions.parallelStream()
                            .filter(region -> region.getName().equals(from))
                            .findFirst();
                    if (!fromOptional.isPresent()) {
                        src.sendMessage(Text.of(from + " doesn't exist."));
                        return CommandResult.empty();
                    }
                    Region region = fromOptional.get();

                    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                            PreparedStatement statement = connection.prepareStatement(UPDATE_REGION)) {
                        statement.setString(1, to);
                        statement.setString(2, region.getWorldUuid().toString());
                        statement.setInt(3, region.getXMin());
                        statement.setInt(4, region.getXMax());
                        statement.setInt(5, region.getZMin());
                        statement.setInt(6, region.getZMax());
                        statement.setDouble(7, region.getTeleportX());
                        statement.setDouble(8, region.getTeleportY());
                        statement.setDouble(9, region.getTeleportZ());
                        statement.setString(10, region.getCreator().toString());
                        statement.setString(11, region.getUuid().toString());

                        statement.executeUpdate();
                    } catch (SQLException e) {
                        throw new CommandException(Text.of("Database error"), e);
                    }
                    src.sendMessage(Text.of("Renamed to " + to + "."));

                    fetchAllRegions();
                    discoveredBy.clear();

                    return CommandResult.success();
                })
                .build();

        CommandSpec reload = CommandSpec.builder()
                .description(Text.of("Reload region caches"))
                .permission("discovery.reload")
                .executor((src, args) -> {
                    fetchAllRegions();
                    discoveredBy.clear();

                    int n = regions.size();
                    src.sendMessage(Text.of("Loaded " + n + " region" + (n != 1 ? "s" : "") + "."));

                    return CommandResult.builder().successCount(n).build();
                })
                .build();


        // The entry point command.
        // Calling this directly takes you to a region's teleport coordinates, if you have discovered it.

        CommandSpec travel = CommandSpec.builder()
                .permission("discovery.travel")
                .arguments(GenericArguments.string(Text.of("destination")))
                .child(list, "list", "?")
                .child(create, "create", "+")
                .child(delete, "delete", "-")
                .child(rename, "rename", "~")
                .child(reload, "reload")
                .executor((src, args) -> {
                    String destination = args.<String>getOne("destination").get();

                    if (src instanceof Player) {
                        Player player = (Player) src;

                        Optional<Region> regionOptional = discoveredBy.computeIfAbsent(player.getUniqueId(), this::regionsFor)
                                .stream()
                                .filter(region -> region.getName().equals(destination))
                                .findFirst();
                        if (!regionOptional.isPresent()) {
                            src.sendMessage(Text.of("You haven't discovered " + destination + "."));
                            return CommandResult.empty();
                        }
                        Region region = regionOptional.get();

                        Optional<World> worldOptional = Sponge.getServer().getWorld(region.getWorldUuid());
                        if (!worldOptional.isPresent()) {
                            throw new CommandException(Text.of("This region is not in any known world."));
                        }
                        World world = worldOptional.get();

                        player.setLocation(world.getLocation(region.getTeleportX(), region.getTeleportY(), region.getTeleportZ()));

                        Vector3d position = player.getLocation().getPosition();
                        world.playSound(SoundTypes.ENDERMAN_TELEPORT, position, 1);

                        return CommandResult.success();
                    } else {
                        src.sendMessage(Text.of("You need to be a player to use this."));
                        return CommandResult.empty();
                    }
                })
                .description(Text.of("Go to a location you've discovered"))
                .build();

        fetchAllRegions();
        discoveredBy.clear();

        Sponge.getCommandManager().register(this, travel, "travel");
    }

    @Listener
    public void onMove(DisplaceEntityEvent.Move event, @Root Player player) {
        Vector3d from = event.getFromTransform().getPosition();
        Vector3d to = event.getToTransform().getPosition();

        Set<Region> discovered = discoveredBy.computeIfAbsent(player.getUniqueId(), this::regionsFor);

        // We only care if the player actually moved across a block boundary
        if (from.getFloorX() != to.getFloorX() || from.getFloorZ() != to.getFloorZ()) {
            // Discover any regions the player hasn't already
            this.regions.stream()
                    .filter(region -> inside(region, to) && !discovered.contains(region))
                    .forEach(region -> discover(player, region));
        }
    }

    private void discover(Player player, Region region) {
        // How exciting.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                PreparedStatement statement = connection.prepareStatement(DISCOVER_REGION)) {
            statement.setString(1, player.getUniqueId().toString());
            statement.setString(2, region.getUuid().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        player.sendTitle(Title.builder()
                .subtitle(Text.builder()
                        .color(TextColors.YELLOW)
                        .append(Text.of("- " + region.getName().toLowerCase(Locale.ENGLISH) + " discovered -"))
                        .build())
                .fadeIn(20)
                .stay(40)
                .fadeOut(20)
                .build());

        SortedSet<Region> updated = discoveredBy.get(player.getUniqueId());
        updated.add(region);

        discoveredBy.replace(player.getUniqueId(), updated);
    }

    private boolean inside(Region region, Vector3d p) {
        return region.getXMin() < p.getFloorX()
                && p.getFloorX() < region.getXMax()
                && region.getZMin() < p.getFloorZ()
                && p.getFloorZ() < region.getZMax();
    }

    private SortedSet<Region> regionsFor(UUID key) {
        SortedSet<Region> regions = new TreeSet<>(Region::compareTo);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                PreparedStatement statement = connection.prepareStatement(REGIONS_DISCOVERED_BY)) {
            statement.setString(1, key.toString());
            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                regions.add(new Region(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        UUID.fromString(rs.getString("world_uuid")),
                        rs.getInt("x_min"),
                        rs.getInt("z_min"),
                        rs.getInt("x_max"),
                        rs.getInt("z_max"),
                        rs.getDouble("teleport_x"),
                        rs.getDouble("teleport_y"),
                        rs.getDouble("teleport_z"),
                        UUID.fromString(rs.getString("creator"))));
            }

            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
            return new TreeSet<>();
        }
        return regions;
    }

    /**
     * Retreive regions from the `regions` table in the database and store them in `regions`.
     */
    private void fetchAllRegions() {
        regions.clear();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                PreparedStatement statement = connection.prepareStatement(ALL_REGIONS);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                regions.add(new Region(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        UUID.fromString(rs.getString("world_uuid")),
                        rs.getInt("x_min"),
                        rs.getInt("z_min"),
                        rs.getInt("x_max"),
                        rs.getInt("z_max"),
                        rs.getDouble("teleport_x"),
                        rs.getDouble("teleport_y"),
                        rs.getDouble("teleport_z"),
                        UUID.fromString(rs.getString("creator"))));
            }
        } catch (SQLException e) {
            logger.error("SQL exception", e);
        }
    }

    private static Text formatRegion(Region region) {
        try {
            return Text.builder()
                    .append(Text.builder()
                            .color(TextColors.GOLD)
                            .onHover(TextActions.showText(Text.of("/travel \"" + region.getName() + "\"")))
                            .append(Text.of(region.getName(),
                                    TextColors.RESET,
                                    " - ")).build())
                    .append(Text.builder()
                            .color(TextColors.GRAY)
                            .onHover(TextActions.showText(Text.builder()
                                    .color(TextColors.GRAY)
                                    .append(Text.of("Creator: " + Sponge.getServer().getGameProfileManager().get(region.getCreator()).get().getName()
                                            .orElse("<unknown>") + "\n"))
                                    .append(Text.of("UUID: " + region.getUuid() + "\n"))
                                    .append(Text.of("World: " + Sponge.getServer().getWorld(region.getWorldUuid())
                                            .map(World::getName)
                                            .orElse("<unknown>") + "\n"))
                                    .append(Text.of("Min: <" + region.getXMin() + ", " + region.getZMin() + ">\n"))
                                    .append(Text.of("Max: <" + region.getXMax() + ", " + region.getZMax() + ">"))
                                    .build()))
                            .onShiftClick(TextActions.insertText(region.getUuid().toString()))
                            .append(Text.of(String.format("<%.2f, %.2f, %.2f>", region.getTeleportX(), region.getTeleportY(), region.getTeleportZ())))
                            .build())
                    .onClick(TextActions.suggestCommand("/travel \"" + region.getName() + "\""))
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return Text.EMPTY;
        }
    }

    /**
     * Builds the structure of the configuration file, initializes it with default values, and saves then loads from disk.
     *
     * @return The default configuration
     * @throws IOException if loading the configuration from disk again fails
     */
    private CommentedConfigurationNode saveDefaultConfig() throws IOException {
        // Getting nodes and giving them comments and values is enough to create a default config layout.

        rootNode.getNode("version")
                .setComment("This is an internal value indicating what format the configuration uses.\n"
                        + "Please don't edit it unless you know what the numbers mean.")
                .setValue(1);

        rootNode.getNode("database", "path")
                .setComment("Path to the SQLite database file, relative to Discovery's configuration folder.\n"
                        + "Include the file extension.")
                .setValue("discovery.db");

        configurationLoader.save(rootNode);
        return configurationLoader.load();
    }

    private void setDatabasePath(String databasePath) {
        this.databasePath = configDir.resolve(databasePath);
    }

}
