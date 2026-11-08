-- pg_trgm acelera los LIKE '%texto%' del catalogo, que hoy recorren la tabla entera.
-- OJO: CREATE EXTENSION exige privilegios de superusuario (o un rol con CREATE EXTENSION
-- delegado). En produccion la extension debe existir antes de aplicar esta migracion.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS actividad_name_trgm_idx
    ON actividad USING gin (translate(lower(name), 'áéíóúüñ', 'aeiouun') gin_trgm_ops);

CREATE INDEX IF NOT EXISTS evento_name_trgm_idx
    ON evento USING gin (translate(lower(name), 'áéíóúüñ', 'aeiouun') gin_trgm_ops);
