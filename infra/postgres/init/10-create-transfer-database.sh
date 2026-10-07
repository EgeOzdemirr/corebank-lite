#!/bin/sh
# Creates the transfer-service database next to the account-service one (each service owns its own database).
# PostgreSQL runs scripts in /docker-entrypoint-initdb.d only when the data volume is empty, i.e. on the first start.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
CREATE DATABASE "$TRANSFER_DB_NAME" OWNER "$POSTGRES_USER";
EOSQL
