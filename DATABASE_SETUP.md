# Database Setup & Production Guide (MySQL & MariaDB)

> **Note for Experienced System Administrators**: If you already have an operational MySQL, MariaDB, or managed cloud database, you can skip to configuring your credentials in `plugins/mc-data-bridge/config.yml`.

---

## Overview

MC Data Bridge requires a centralized **MySQL** or **MariaDB** database to coordinate player state and distributed lock fencing across multiple Minecraft servers.

Your database can run on:
- A **managed cloud database service** (AWS RDS, DigitalOcean Managed Database, PlanetScale, GCP Cloud SQL).
- A **quick Docker container** on your server host.
- A **dedicated Linux server / VPS** on your private network.

---

## Option 1: Managed Cloud Database Services (Recommended for Zero-Ops)

For high-availability networks where you do not want to manage raw database servers, hosting on a managed cloud database provides automated backups, high availability, and SSL encryption out of the box:

- **DigitalOcean Managed Databases**: Simple provisioned MariaDB/MySQL clusters with automated daily snapshots.
- **Amazon RDS for MySQL / MariaDB**: Enterprise multi-AZ replication, Point-In-Time-Recovery (PITR), and automated encrypted backups.
- **Google Cloud SQL / Aiven / PlanetScale**: Fully managed relational databases with automatic SSL connection handling.

To connect MC Data Bridge to a managed cloud database requiring SSL, add JDBC SSL properties in `config.yml`:

```yaml
database:
  type: mysql
  host: your-managed-db.example.com
  port: 3306
  database: minecraft
  username: databridge_user
  password: "YourSecurePassword"
  properties:
    useSSL: true
    requireSSL: true
    verifyServerCertificate: true
```

---

## Option 2: Quick Docker Setup (Recommended for Local/Self-Hosted Networks)

If you have Docker installed on your host server, you can spin up a MariaDB container in seconds:

```bash
docker run -d \
  --name mc-databridge-db \
  --restart always \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=YourSuperSecretRootPassword \
  -e MYSQL_DATABASE=minecraft \
  -e MYSQL_USER=databridge_user \
  -e MYSQL_PASSWORD=YourSecureUserPassword \
  -v mariadb_data:/var/lib/mysql \
  mariadb:latest
```

---

## Option 3: Bare-Metal / Linux Host Setup (Ubuntu / Debian)

If you run dedicated Linux hardware or a VPS:

### 1. Install MariaDB Server

```bash
sudo apt update
sudo apt install -y mariadb-server
sudo systemctl enable --now mariadb
```

### 2. Secure Installation & Create Database

Run the security script:

```bash
sudo mysql_secure_installation
```

Log into the MariaDB shell:

```bash
sudo mysql -u root -p
```

Create a dedicated database and grant least-privilege permissions (`SELECT`, `INSERT`, `UPDATE`, `CREATE`, `ALTER`):

```sql
CREATE DATABASE IF NOT EXISTS minecraft CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'databridge_user'@'%' IDENTIFIED BY 'YourSecureUserPassword';

GRANT SELECT, INSERT, UPDATE, CREATE, ALTER ON minecraft.* TO 'databridge_user'@'%';

FLUSH PRIVILEGES;
EXIT;
```

---

## 💾 Offsite Backup Strategy vs. Internal Redundancy

MC Data Bridge includes an optional internal redundancy feature (`database.backups.enabled: true` in `config.yml`), which exports local JSON snapshots to the plugin's `backups/` directory.

> **Critical Note on Internal Redundancy vs. True Backups**: 
> As explicitly noted in `config.yml`, writing local JSON snapshots to the local Minecraft server filesystem is **local redundancy**, not a true backup. If your host machine experiences hardware failure, disk corruption, cloud node termination, or a datacenter outage, local files in `plugins/mc-data-bridge/backups/` will be destroyed alongside the server.
>
> For production environments, the internal redundancy feature is disabled by default (`database.backups.enabled: false`) to avoid giving administrators a false sense of security.

A true enterprise disaster recovery strategy requires **automated, offsite database dumps**.

### Recommended Offsite Backup Workflow (`mysqldump` + Remote Storage)

Set up a daily cron job that executes a `mysqldump` of your database, compresses it, and securely pushes it to offsite cloud storage (e.g. AWS S3, Cloudflare R2, Backblaze B2, or a remote backup server).

#### 1. Automated Offsite Backup Script (`/usr/local/bin/databridge-backup.sh`)

```bash
#!/usr/bin/env bash
set -euo pipefail

TIMESTAMP=$(date +"%Y-%m-%d_%H%M%S")
BACKUP_DIR="/tmp/db_backups"
BACKUP_FILE="${BACKUP_DIR}/databridge_${TIMESTAMP}.sql.gz"
REMOTE_S3_BUCKET="s3://your-company-offsite-backups/mc-data-bridge"

mkdir -p "${BACKUP_DIR}"

# 1. Dump database cleanly without locking active tables
mysqldump -u databridge_user -p'YourSecureUserPassword' \
  --single-transaction \
  --quick \
  --lock-tables=false \
  minecraft | gzip > "${BACKUP_FILE}"

# 2. Push compressed dump to offsite cloud storage (AWS S3 / Cloudflare R2 / Backblaze B2)
aws s3 cp "${BACKUP_FILE}" "${REMOTE_S3_BUCKET}/databridge_${TIMESTAMP}.sql.gz"

# 3. Clean up local temporary file
rm -f "${BACKUP_FILE}"
```

#### 2. Schedule Daily Cron Job (`crontab -e`)

```cron
# Run offsite database backup every night at 3:00 AM
0 3 * * * /usr/local/bin/databridge-backup.sh >/dev/null 2>&1
```

---

## Production Security & Tuning Best Practices

1. **Firewall Access Controls**: Restrict TCP port `3306` access solely to the IP addresses of your Minecraft backend servers:
   ```bash
   sudo ufw allow from <MINECRAFT_SERVER_IP> to any port 3306 proto tcp
   ```
2. **Dedicated SQL User**: Never use the database `root` account in `config.yml`. Restrict the plugin user to your specific database.
3. **Database Connection Limits**: For networks running 10+ backend servers, ensure `max_connections` in `/etc/mysql/mariadb.conf.d/50-server.cnf` is tuned appropriately (e.g. `max_connections = 250`).
