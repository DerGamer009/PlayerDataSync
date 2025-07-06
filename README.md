# PlayerDataSync

A simple Bukkit/Spigot plugin for Minecraft 1.21+ that synchronizes player data using either a MySQL or SQLite database. This project is built with Maven.
Player inventories, experience, health and more are stored in the configured
database whenever they leave the server and restored when they join again.

## Building

Run the following command in the project directory:

```bash
mvn package
```

The resulting jar can be found in `target/`.

The build process uses the Maven Shade plugin to bundle required
dependencies (such as bStats) directly into the final jar, so no
additional libraries need to be installed on the server.

## Configuration

`config.yml` contains the database connection settings and options to control which
player data should be synchronized:

```yaml
database:
  type: mysql # or sqlite
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    user: root
    password: password
  sqlite:
    file: plugins/PlayerDataSync/playerdata.db

sync:
  coordinates: true
  xp: true
  gamemode: true
  enderchest: true
  inventory: true
  health: true
  hunger: true
  position: true
autosave:
  interval: 5
language: en
metrics: true
```

`metrics` controls whether anonymous usage statistics are sent to
[bStats](https://bstats.org/). Set it to `false` if you prefer to
opt out of metrics collection.

`autosave.interval` controls how often (in minutes) the plugin saves all online
players to the database. Set it to `0` to disable automatic saves.

Update the database values to match your environment. Set any of the `sync` options to
`false` if you want to skip syncing that particular data type.
