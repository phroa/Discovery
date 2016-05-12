Discovery [![Travis](https://img.shields.io/travis/phroa/Discovery.svg?maxAge=2592000)]() | [![GitHub tag](https://img.shields.io/github/tag/phroa/Discovery.svg?maxAge=2592000)]()
=========

Fast-travel system for [Sponge](https://spongepowered.org) based on the idea that you need to explore a region yourself before you can travel there instantly.

## Installation

Place [`discovery-xxx.jar`](https://ore-staging.spongepowered.org/phroa/Discovery/versions) in your Sponge server's `mods` folder.

## Concepts

- "Regions" are rectangular areas with a minimum and maximum X and Z coordinate in a particular world, from bedrock up to the sky.
    - These coordinates are integers.
    - You can technically make a region with fewer than four square meters contained inside, but you won't be able to discover it.
    - Walking through the outside edge of a region is not considered "discovering" it. You have to be at least one block inside.

- Regions must have a "teleport position" which is the location people will be taken when they use `/travel <destination>`.
    - This position is given as a decimal number, which is **not** rounded. This is so you can specify precisely where you want players to be.
    - If you want players to end up in the middle, and not the far corner of, a block, be sure to add (or subtract) .5 to the X and Z coordinate.
    - Similarly, the Y coordinate is where players' feet will be teleported to. Add one to this if you don't want them in a block.

- Discovery uses an SQLite database, located in `config/discovery/discovery.db` by default, to store regions. You may modify this directly if `/travel reload` is issued after.
    - Schema is specified as a series of migrations in the `resources/db/migration` folder inside `discovery-xxx.jar`, if you need them.

## Commands

Command | Description | Permission
--------|-------------|-----------
`/travel` | Main command. | `discovery.travel`
`/travel <destination>` | Travel to `<destination>` if you've discovered it. | `discovery.travel`
`/travel list` | List the regions you've discovered, or all regions if used from the console. | `discovery.list`
`/travel create <name> <x1> <z1> <x2> <z2> <teleportX> <teleportY> <teleportZ>` | Create a new region. This does not automatically discover the region for anybody. | `discovery.create`
`/travel + <name> <x1> <z1> <x2> <z2> <teleportX> <teleportY> <teleportZ>` | See above. | `discovery.create`
`/travel delete <uuid>` | Remove a region by its region UUID. This number can be found inside the database, or by hovering over the gray text in `/travel list`. | `discovery.delete`
`/travel - <uuid>` | See above. | `discovery.delete`
`/travel rename <old-name> <new-name>` | Rename a region from `<old-name>` to `<new-name>`. | `discovery.rename`
`/travel ~ <old-name> <new-name>` | See above. | `discovery.rename`
`/travel reload` | Replace the internal lists of regions with whatever is in the database. | `discovery.reload`

## Screenshots

<blockquote class="imgur-embed-pub" lang="en" data-id="a/QC9SC"><a href="//imgur.com/a/QC9SC">Discovery</a></blockquote><script async src="//s.imgur.com/min/embed.js" charset="utf-8"></script>

## Contributing

- Ensure your code builds, or mark the pull request as a work in progress.
- Use the [Sponge code style](https://github.com/SpongePowered/SpongeAPI/tree/master/extra) everywhere
- Write decent commit messages

## License

GPLv3+:

```text
Discovery

Copyright (C) phroa

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```
