#!/usr/bin/env bash
#
# Opens a MySQL prompt against the local development server.
# Pass a database name to open straight into it, for example:
#   ./scripts/mysql-client.sh college_timetable
set -euo pipefail

MYSQL_HOME="${MYSQL_HOME:-$HOME/.local/opt/mysql}"
MYSQL_DATA="${MYSQL_DATA:-$HOME/.local/var/mysql-timetable}"
SOCKET="$MYSQL_DATA/mysql.sock"
DB="${1:-college_timetable}"

exec "$MYSQL_HOME/bin/mysql" --socket="$SOCKET" -u root "$DB"
