-- pg_trgm acelera los LIKE '%texto%' del catalogo, que hoy recorren la tabla entera.
-- Si el rol de Flyway no puede crear extensiones, la migracion no debe tumbar el arranque:
-- deja los indices para cuando un operador habilite pg_trgm.
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION
    WHEN insufficient_privilege THEN
        RAISE NOTICE 'V9: sin privilegios para CREATE EXTENSION pg_trgm; se omiten indices trigram';
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        CREATE INDEX IF NOT EXISTS actividad_name_trgm_idx
            ON actividad USING gin (translate(lower(name), 'áéíóúüñ', 'aeiouun') gin_trgm_ops);

        CREATE INDEX IF NOT EXISTS evento_name_trgm_idx
            ON evento USING gin (translate(lower(name), 'áéíóúüñ', 'aeiouun') gin_trgm_ops);
    ELSE
        RAISE NOTICE 'V9: extension pg_trgm ausente; se omiten indices trigram';
    END IF;
END $$;
