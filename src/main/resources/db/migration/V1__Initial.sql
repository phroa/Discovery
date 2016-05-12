--
-- Discovery
--
-- Copyright (C) phroa <jack@phroa.net>
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with this program.  If not, see <http://www.gnu.org/licenses/>.
--

CREATE TABLE regions (
  uuid       VARCHAR NOT NULL PRIMARY KEY,
  name       VARCHAR NOT NULL UNIQUE,
  world_uuid VARCHAR NOT NULL,
  x_min      INT     NOT NULL,
  z_min      INT     NOT NULL,
  x_max      INT     NOT NULL,
  z_max      INT     NOT NULL,
  teleport_x DOUBLE  NOT NULL,
  teleport_y DOUBLE  NOT NULL,
  teleport_z DOUBLE  NOT NULL,
  creator    VARCHAR NOT NULL
);

CREATE TABLE discovered_regions (
  player_uuid VARCHAR NOT NULL,
  region_uuid VARCHAR NOT NULL,
  FOREIGN KEY (region_uuid) REFERENCES regions (uuid)
);
