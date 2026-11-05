#!/bin/sh
# Inicializa las bases de datos y los roles de PostgreSQL del stack integrado.
#
# Se ejecuta una sola vez, cuando el volumen de datos esta vacio (initdb del entrypoint
# oficial de postgres). Cada servicio conserva su base separada y ms-03 mantiene la
# separacion DDL (ms03_flyway) / DML (ms03_app) que documenta su README.
set -eu

echo "[postgres-init] creando bases de datos"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v dbuser="$POSTGRES_USER" <<'EOSQL'
SELECT format('CREATE DATABASE smxplore_users OWNER %I', :'dbuser')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'smxplore_users') \gexec

SELECT format('CREATE DATABASE smxplore OWNER %I', :'dbuser')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'smxplore') \gexec

SELECT format('CREATE DATABASE activities_events OWNER %I', :'dbuser')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'activities_events') \gexec
EOSQL

echo "[postgres-init] creando roles de ms-03"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v flyway_password="${MS03_FLYWAY_PASSWORD:?MS03_FLYWAY_PASSWORD is required}" \
  -v app_password="${MS03_APP_PASSWORD:?MS03_APP_PASSWORD is required}" <<'EOSQL'
SELECT format('CREATE ROLE ms03_flyway LOGIN PASSWORD %L', :'flyway_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ms03_flyway') \gexec

SELECT format('CREATE ROLE ms03_app LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ms03_app') \gexec
EOSQL

echo "[postgres-init] otorgando privilegios en activities_events"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname activities_events <<'EOSQL'
GRANT CONNECT ON DATABASE activities_events TO ms03_flyway, ms03_app;
GRANT USAGE, CREATE ON SCHEMA public TO ms03_flyway;
GRANT USAGE ON SCHEMA public TO ms03_app;

ALTER DEFAULT PRIVILEGES FOR ROLE ms03_flyway IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ms03_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ms03_flyway IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO ms03_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ms03_flyway IN SCHEMA public
  GRANT EXECUTE ON FUNCTIONS TO ms03_app;
EOSQL

echo "[postgres-init] listo"
