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

import java.util.UUID;

/**
 * Represents a region that a player can discover.
 */
public class Region implements Comparable<Region> {

    private final UUID uuid;
    private final String name;
    private final UUID worldUuid;
    private final int xMin;
    private final int zMin;
    private final int xMax;
    private final int zMax;
    private final double teleportX;
    private final double teleportY;
    private final double teleportZ;
    private final UUID creator;

    public Region(UUID uuid, String name, UUID worldUuid, int xMin, int zMin, int xMax, int zMax, double teleportX, double teleportY,
            double teleportZ, UUID creator) {
        this.uuid = uuid;
        this.name = name;
        this.worldUuid = worldUuid;
        this.xMin = xMin;
        this.zMin = zMin;
        this.xMax = xMax;
        this.zMax = zMax;
        this.teleportX = teleportX;
        this.teleportY = teleportY;
        this.teleportZ = teleportZ;
        this.creator = creator;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public UUID getWorldUuid() {
        return worldUuid;
    }

    public int getXMin() {
        return xMin;
    }

    public int getZMin() {
        return zMin;
    }

    public int getXMax() {
        return xMax;
    }

    public int getZMax() {
        return zMax;
    }

    public double getTeleportX() {
        return teleportX;
    }

    public double getTeleportY() {
        return teleportY;
    }

    public double getTeleportZ() {
        return teleportZ;
    }

    public UUID getCreator() {
        return creator;
    }

    @Override
    public boolean equals(Object o) {
        return o != null && o instanceof Region && ((Region) o).getUuid().equals(uuid);
    }

    @Override
    public int compareTo(Region other) {
        // Users probably don't expect case-sensitive ordering in lists and such.
        return String.CASE_INSENSITIVE_ORDER.compare(name, other.getName());
    }

}
