CREATE TABLE IF NOT EXISTS evento (
    id                  uuid PRIMARY KEY,
    sitio_id            uuid NOT NULL,
    name                varchar(150) NOT NULL,
    category            varchar(50),
    status              varchar(30),
    price               numeric(12, 2) NOT NULL DEFAULT 0,
    max_capacity        integer,
    starts_at           timestamptz NOT NULL,
    ends_at             timestamptz NOT NULL,
    description         text,
    expected_attendance integer NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS actividad (
    id            uuid PRIMARY KEY,
    sitio_id      uuid NOT NULL,
    guia_id       uuid,
    name          varchar(150) NOT NULL,
    type          varchar(50),
    status        varchar(30),
    price         numeric(12, 2) NOT NULL DEFAULT 0,
    max_capacity  integer,
    duration_mins integer,
    starts_at     timestamptz NOT NULL,
    ends_at       timestamptz NOT NULL,
    description   text
);

CREATE INDEX IF NOT EXISTS evento_sitio_idx ON evento (sitio_id);
CREATE INDEX IF NOT EXISTS evento_name_idx ON evento (name);
CREATE INDEX IF NOT EXISTS actividad_sitio_idx ON actividad (sitio_id);
CREATE INDEX IF NOT EXISTS actividad_category_idx ON actividad (type);
