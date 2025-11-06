# MC Data Bridge

MC Data Bridge is a robust, high-performance Spigot/Paper plugin designed to seamlessly synchronize player data across multiple Minecraft servers. It ensures that players have a consistent experience, retaining their health, hunger, experience, inventory, and more as they move between linked servers.

## Features

  * **Fully Asynchronous:** All database operations are performed on a separate thread, ensuring that your server's main thread is never blocked. This means no lag or crashes, even if your database is slow to respond.
  * **Race-Condition Safe:** A database-level locking mechanism prevents data loss when players switch servers quickly. This guarantees that the most recent player data is always loaded.
  * **Version-Independent Item Serialization:** Player inventories are serialized using a robust NBT-based method, which prevents data loss when you update your Minecraft server to a new version.
  * **Cross-Server Player Data Sync:** Synchronizes core player data including:
      * Health
      * Food Level & Saturation
      * Experience (Total XP, current XP, and Level)
      * Inventory Contents
      * Armor Contents
      * Active Potion Effects
  * **Resilient Connection Pooling:** Uses HikariCP with optimized settings to ensure that the database connection is resilient to network issues and database restarts.
  * **Configurable & Flexible:** Easily connect to your MySQL database, configure SSL, and create distinct synchronization groups for different sets of servers.

## Installation

1.  **Build the Plugin:**
      * Navigate to the plugin's root directory in your terminal.
      * Run `mvn clean package` to build the plugin.
      * The compiled JAR file (`mc-data-bridge-1.21.8.*.jar`) will be located in the `target/` directory.
2.  **Deploy to Servers:**
      * Copy the `mc-data-bridge-1.21.8.*.jar` file into the `plugins/` folder of each PaperMC server you wish to synchronize.

## Configuration

A `config.yml` file will be generated in the `plugins/mc-data-bridge/` folder after the first run. You must update this file with your MySQL database credentials. The file is pre-configured with optimized settings for performance and stability.

```yaml
# MySQL Database Configuration
database:
  host: localhost
  port: 3306
  database: minecraft
  username: user
  password: password

  # A list of JDBC properties to apply.
  # These are recommended, but you can change/add/remove as needed.
  # This is where you would enable SSL (e.g., useSSL: true)
  properties:
    useSSL: false
    allowPublicKeyRetrieval: true

  # HikariCP Connection Pool Settings
  # These settings are optimized for resilience and performance.
  # It is recommended to leave these at their default values unless you are an experienced administrator.
  pool-settings:
    maximum-pool-size: 10
    minimum-idle: 10
    max-lifetime: 1800000 # 30 minutes
    connection-timeout: 5000 # 5 seconds
    idle-timeout: 600000 # 10 minutes

  # MySQL JDBC Optimizations
  # These are advanced settings for the MySQL driver.
  # Do not change these unless you know what you are doing.
  optimizations:
    cache-prep-stmts: true
    prep-stmt-cache-size: 250
    prep-stmt-cache-sql-limit: 2048
    use-server-prep-stmts: true
    use-local-session-state: true
    rewrite-batched-statements: true
    cache-result-set-metadata: true
    cache-server-configuration: true
    elide-set-auto-commits: true
    maintain-time-stats: false

# Set to true to enable verbose debugging messages in the server console.
# This can be useful for diagnosing issues, but should be false for normal operation.
debug: false
```

  * **`database.host`**: Your MySQL database host (e.g., `localhost`, `127.0.0.1`, a remote IP, or a Docker named volume if your database is running in Docker).
  * **`database.port`**: The port your MySQL database is running on (default is `3306`).
  * **`database.database`**: The name of the database to use for player data.
  * **`database.username`**: The username for connecting to your database.
  * **`database.password`**: The password for the database user.
  * **`database.properties`**: A flexible section to add any JDBC driver properties. This is where you would **enable SSL** (e.g., `useSSL: true`) if your database host requires it.
  * **`database.pool-settings`** & **`database.optimizations`**: Advanced settings for the database connection pool and JDBC driver. It is highly recommended to leave these at their defaults unless you are a database administrator.
  * **`debug`**: Set to `true` to enable verbose debugging messages in the server console. Set to `false` for normal operation.

### Synchronizing Multiple Server Groups

To sync player data between specific groups of servers (e.g., a Towny server and a Towny resource server, separate from a Creative server), simply configure the `database.database` (or even entirely different `database.host`, `username`, `password`) to match for the servers you want to link.

**Example:**

  * **Towny Servers (Towny-Main, Towny-Resource):**
    ```yaml
    database:
      host: my_db_host
      port: 3306
      database: towny_player_data # All Towny servers use this database
      username: towny_user
      password: towny_password
      # ... (properties, pool-settings, etc.)
    debug: false
    ```
  * **Creative Servers (Creative-Build, Creative-Minigame):**
    ```yaml
    database:
      host: my_db_host
      port: 3306
      database: creative_player_data # All Creative servers use this database
      username: creative_user
      password: creative_password
      # ... (properties, pool-settings, etc.)
    debug: false
    ```

## Usage

1.  **Add the JAR:** Place the `mc-data-bridge-1.21.8.*.jar` file into the `plugins/` folder of all your PaperMC servers.
2.  **Configure:** Edit the `config.yml` in each server's `plugins/mc-data-bridge/` folder with the appropriate database credentials.
3.  **Restart Servers:** Restart your Minecraft servers to apply the plugin and configuration changes.
4.  **Enjoy\!** Players can now seamlessly switch between your linked servers, and their data will be synchronized automatically and safely.

## Important Notes

* **Database Requirement:** This plugin requires a **MySQL or MariaDB database** to function. Ensure your database server is accessible from your Minecraft servers.
* **Security Best Practice:** For production servers, it is strongly recommended to create a dedicated MySQL user for this plugin (and ideally for *each* plugin). **Do not use your shared `root` user.** As long as the credentials in `config.yml` belong to a user with the required permissions, the plugin will function.
    * *Minimum required permissions:* The user only needs `SELECT`, `INSERT`, `UPDATE`, `CREATE`, and `ALTER` on the database specified in the config.
* **Connectivity & Firewalls:** The server running this plugin (whether on bare metal or in a **Docker container**) must be able to open a network connection to your database's `host` and `port`. If you are using Docker, ensure your container's networking rules allow this.
* **Automatic Schema:** The plugin will automatically create and update the `player_data` table in your specified database. The schema includes `uuid`, `data` (LONGTEXT), `is_locked` (BOOLEAN), and `last_updated` (TIMESTAMP).