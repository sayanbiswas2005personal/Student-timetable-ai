#!/usr/bin/env bash
#
# Starts a local MySQL server without Docker, for machines where Homebrew is not available.
#
# It uses the MySQL tarball unpacked under ~/.local/opt/mysql and stores its data under
# ~/.local/var/mysql-timetable. Nothing in the project directory is touched.
#
#   ./scripts/mysql-start.sh      start the server (idempotent)
#   ./scripts/mysql-stop.sh       stop it
#   ./scripts/mysql-client.sh     open a MySQL prompt
set -euo pipefail

MYSQL_HOME="${MYSQL_HOME:-$HOME/.local/opt/mysql}"
MYSQL_DATA="${MYSQL_DATA:-$HOME/.local/var/mysql-timetable}"
CONFIG="$MYSQL_HOME/my.cnf"
SOCKET="$MYSQL_DATA/mysql.sock"

if [[ ! -x "$MYSQL_HOME/bin/mysqld" ]]; then
  echo "MySQL was not found at $MYSQL_HOME." >&2
  echo "Install Docker and run 'docker compose up -d', or install MySQL and set MYSQL_HOME." >&2
  exit 1
fi

mkdir -p "$MYSQL_DATA/data" "$MYSQL_DATA/logs" "$MYSQL_DATA/tmp"

if [[ ! -f "$CONFIG" ]]; then
  cat > "$CONFIG" <<CFG
[mysqld]
basedir=$MYSQL_HOME
datadir=$MYSQL_DATA/data
tmpdir=$MYSQL_DATA/tmp
socket=$SOCKET
port=${MYSQL_PORT:-3307}
bind-address=127.0.0.1
pid-file=$MYSQL_DATA/mysqld.pid
log-error=$MYSQL_DATA/logs/error.log
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
CFG
  echo "Wrote $CONFIG"
fi

if [[ ! -d "$MYSQL_DATA/data/mysql" ]]; then
  echo "Initialising the data directory (first run only)..."
  "$MYSQL_HOME/bin/mysqld" --defaults-file="$CONFIG" --initialize-insecure
fi

if "$MYSQL_HOME/bin/mysqladmin" --socket="$SOCKET" -u root ping > /dev/null 2>&1; then
  echo "MySQL is already running on port ${MYSQL_PORT:-3307}."
  exit 0
fi

echo "Starting MySQL..."
nohup "$MYSQL_HOME/bin/mysqld" --defaults-file="$CONFIG" > /dev/null 2>&1 &

for _ in $(seq 1 30); do
  if "$MYSQL_HOME/bin/mysqladmin" --socket="$SOCKET" -u root ping > /dev/null 2>&1; then
    echo "MySQL is up. Socket: $SOCKET  Port: ${MYSQL_PORT:-3307}"
    exit 0
  fi
  sleep 1
done

echo "MySQL did not start. Check $MYSQL_DATA/logs/error.log" >&2
exit 1
