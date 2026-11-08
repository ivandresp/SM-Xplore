#!/bin/sh
# Inicializa las bases de datos y los roles de PostgreSQL del stack integrado.
#
# Se ejecuta una sola vez, cuando el volumen de datos esta vacio (initdb del entrypoint
# oficial de postgres).
#
# PLAT-05: cada servicio de aplicacion entra con su propio rol LOGIN, dueno de su base
# y sin privilegios fuera de ella. Antes, ms-01 y ms-04 usaban el superusuario del
# cluster (POSTGRES_USER); ahora ese usuario queda solo como acceso administrativo /
# break-glass (y para el exporter). ms-03 conserva ademas la separacion DDL
# (ms03_flyway) / DML (ms03_app) que documenta su README.
#
# Variables: MS01_APP_PASSWORD, MS03_FLYWAY_PASSWORD, MS03_APP_PASSWORD y
# MS04_APP_PASSWORD. En produccion el compose las exige con `:?`; en desarrollo los
# defaults coinciden con los del compose local.
set -eu

: "${MS01_APP_PASSWORD:=ms01_app}"
: "${MS03_FLYWAY_PASSWORD:=ms03_flyway}"
: "${MS03_APP_PASSWORD:=ms03_app}"
: "${MS04_APP_PASSWORD:=ms04_app}"

echo "[postgres-init] creando roles por servicio"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v ms01_password="$MS01_APP_PASSWORD" \
  -v ms03_flyway_password="$MS03_FLYWAY_PASSWORD" \
  -v ms03_app_password="$MS03_APP_PASSWORD" \
  -v ms04_password="$MS04_APP_PASSWORD" <<'EOSQL'
SELECT format('CREATE ROLE ms01_app LOGIN PASSWORD %L', :'ms01_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ms01_app') \gexec

SELECT format('CREATE ROLE ms03_flyway LOGIN PASSWORD %L', :'ms03_flyway_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ms03_flyway') \gexec

SELECT format('CREATE ROLE ms03_app LOGIN PASSWORD %L', :'ms03_app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ms03_app') \gexec

SELECT format('CREATE ROLE ms04_app LOGIN PASSWORD %L', :'ms04_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ms04_app') \gexec
EOSQL

echo "[postgres-init] creando bases de datos con su rol dueno"

# smxplore_users pertenece a ms-01 y smxplore a ms-04: ser dueno de la base da DDL
# sobre su propio esquema public (necesario para las migraciones de ms-01), pero no
# permite crear ni leer bases ajenas ni escalar a superusuario.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<'EOSQL'
SELECT 'CREATE DATABASE smxplore_users OWNER ms01_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'smxplore_users') \gexec

SELECT format('CREATE DATABASE smxplore OWNER %I', 'ms04_app')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'smxplore') \gexec

SELECT format('CREATE DATABASE activities_events OWNER %I', current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'activities_events') \gexec
EOSQL

echo "[postgres-init] cerrando el CONNECT por defecto de PUBLIC"

# Sin este REVOKE cualquier rol LOGIN del cluster puede conectarse a cualquier base
# (PUBLIC tiene CONNECT por defecto). Cada base queda accesible solo para su dueno y
# para los roles explicitamente autorizados.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<'EOSQL'
REVOKE ALL ON DATABASE smxplore_users FROM PUBLIC;
GRANT CONNECT ON DATABASE smxplore_users TO ms01_app;

REVOKE ALL ON DATABASE smxplore FROM PUBLIC;
GRANT CONNECT ON DATABASE smxplore TO ms04_app;

REVOKE ALL ON DATABASE activities_events FROM PUBLIC;
GRANT CONNECT ON DATABASE activities_events TO ms03_flyway, ms03_app;
EOSQL

echo "[postgres-init] otorgando privilegios en activities_events (ms-03)"

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

-- ms-01 y ms-04 no pueden conectarse a la base de ms-03 ni a la de los demas.
REVOKE ALL ON DATABASE activities_events FROM PUBLIC;
GRANT CONNECT ON DATABASE activities_events TO ms03_flyway, ms03_app;
EOSQL

echo "[postgres-init] listo"
