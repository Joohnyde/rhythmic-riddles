#!/usr/bin/env bash
set -euo pipefail

YAML_FILE="${1:?Usage: setup-e2e-schema.sh <application-e2e.yml> <clean:true|false>}"
CLEAN="${2:-false}"

SOURCE_SCHEMA="public"
TARGET_SCHEMA="e2e"

if [[ ! -f "$YAML_FILE" ]]; then
  echo "YAML file not found: $YAML_FILE"
  exit 1
fi

read_yaml_value() {
  local key="$1"

  python3 - "$YAML_FILE" "$key" <<'PY'
import os
import re
import sys

path = sys.argv[1]
key = sys.argv[2]

text = open(path, encoding="utf-8").read()

patterns = {
    "url": r"(?m)^\s*url:\s*(.+?)\s*$",
    "username": r"(?m)^\s*username:\s*(.+?)\s*$",
    "password": r"(?m)^\s*password:\s*(.+?)\s*$",
}

match = re.search(patterns[key], text)
if not match:
    print("")
    sys.exit(0)

value = match.group(1).strip().strip('"').strip("'")

# Resolve Spring placeholders like ${DB_PASSWORD:default}
placeholder = re.fullmatch(r"\$\{([^:}]+):?([^}]*)\}", value)
if placeholder:
    env_name, default = placeholder.groups()
    value = os.environ.get(env_name, default)

print(value)
PY
}

JDBC_URL="$(read_yaml_value url)"
DB_USER="$(read_yaml_value username)"
DB_PASSWORD="$(read_yaml_value password)"

if [[ -z "$JDBC_URL" ]]; then
  echo "Could not read spring.datasource.url from $YAML_FILE"
  exit 1
fi

if [[ -z "$DB_USER" ]]; then
  echo "Could not read spring.datasource.username from $YAML_FILE"
  exit 1
fi

DB_HOST="$(echo "$JDBC_URL" | sed -E 's#jdbc:postgresql://([^:/?]+).*#\1#')"
DB_PORT="$(echo "$JDBC_URL" | sed -E 's#jdbc:postgresql://[^:/?]+:([0-9]+).*#\1#')"
DB_NAME="$(echo "$JDBC_URL" | sed -E 's#jdbc:postgresql://[^/]+/([^?]+).*#\1#')"

if [[ "$DB_PORT" == "$JDBC_URL" ]]; then
  DB_PORT="5432"
fi

export PGPASSWORD="$DB_PASSWORD"

echo "Preparing E2E schema"
echo "DB: ${DB_NAME}"
echo "Host: ${DB_HOST}"
echo "Port: ${DB_PORT}"
echo "User: ${DB_USER}"
echo "Clean: ${CLEAN}"

if [[ "$CLEAN" == "true" ]]; then
  echo "Dropping schema ${TARGET_SCHEMA}"
  psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -v ON_ERROR_STOP=1 \
    -c "DROP SCHEMA IF EXISTS ${TARGET_SCHEMA} CASCADE;"
fi

echo "Creating schema ${TARGET_SCHEMA}"
psql \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_USER" \
  -d "$DB_NAME" \
  -v ON_ERROR_STOP=1 \
  -c "CREATE SCHEMA IF NOT EXISTS ${TARGET_SCHEMA};"

EXISTING_TABLES="$(psql \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_USER" \
  -d "$DB_NAME" \
  -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='${TARGET_SCHEMA}';")"

if [[ "$EXISTING_TABLES" != "0" && "$CLEAN" != "true" ]]; then
  echo "Schema ${TARGET_SCHEMA} is not empty. Run with -De2e.clean=true."
  exit 0
fi

echo "Copying ${SOURCE_SCHEMA} schema into ${TARGET_SCHEMA}"

pg_dump \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_USER" \
  -d "$DB_NAME" \
  --schema="$SOURCE_SCHEMA" \
  --no-owner \
  --no-privileges \
| sed \
  -e "s/CREATE SCHEMA ${SOURCE_SCHEMA};/CREATE SCHEMA IF NOT EXISTS ${TARGET_SCHEMA};/g" \
  -e "s/SCHEMA ${SOURCE_SCHEMA}/SCHEMA ${TARGET_SCHEMA}/g" \
  -e "s/${SOURCE_SCHEMA}\\./${TARGET_SCHEMA}./g" \
  -e "s/SET search_path = ${SOURCE_SCHEMA}/SET search_path = ${TARGET_SCHEMA}/g" \
| psql \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_USER" \
  -d "$DB_NAME" \
  -v ON_ERROR_STOP=1

echo "E2E schema ready."
