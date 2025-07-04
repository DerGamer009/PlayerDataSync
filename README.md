# PlayerDataSync

A simple Bukkit/Spigot plugin for Minecraft 1.21+ that synchronizes player data using either a MySQL or SQLite database. This project is built with Maven.

## Building

Run the following command in the project directory:

```bash
mvn package
```

The resulting jar can be found in `target/`.

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
```

Update the database values to match your environment. Set any of the `sync` options to
`false` if you want to skip syncing that particular data type.
