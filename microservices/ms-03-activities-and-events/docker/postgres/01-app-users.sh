#!/bin/sh
set -eu

: "${MS03_FLYWAY_PASSWORD:?MS03_FLYWAY_PASSWORD is required}"
: "${MS03_APP_PASSWORD:?MS03_APP_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v flyway_password="$MS03_FLYWAY_PASSWORD" \
  -v app_password="$MS03_APP_PASSWORD" \
  -v dbname="$POSTGRES_DB" <<'EOSQL'
SELECT format('CREATE ROLE ms03_flyway LOGIN PASSWORD %L', :'flyway_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ms03_flyway') \gexec

SELECT format('CREATE ROLE ms03_app LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ms03_app') \gexec

GRANT CONNECT ON DATABASE :"dbname" TO ms03_flyway, ms03_app;
GRANT USAGE, CREATE ON SCHEMA public TO ms03_flyway;
GRANT USAGE ON SCHEMA public TO ms03_app;

ALTER DEFAULT PRIVILEGES FOR ROLE ms03_flyway IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ms03_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ms03_flyway IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO ms03_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ms03_flyway IN SCHEMA public
  GRANT EXECUTE ON FUNCTIONS TO ms03_app;
EOSQL
