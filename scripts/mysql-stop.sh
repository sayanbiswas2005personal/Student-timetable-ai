#!/usr/bin/env bash
#
# Stops the local MySQL server started by mysql-start.sh.
set -euo pipefail

MYSQL_HOME="${MYSQL_HOME:-$HOME/.local/opt/mysql}"
MYSQL_DATA="${MYSQL_DATA:-$HOME/.local/var/mysql-timetable}"
SOCKET="$MYSQL_DATA/mysql.sock"

if "$MYSQL_HOME/bin/mysqladmin" --socket="$SOCKET" -u root shutdown 2>/dev/null; then
  echo "MySQL stopped."
else
  echo "MySQL was not running."
fi
