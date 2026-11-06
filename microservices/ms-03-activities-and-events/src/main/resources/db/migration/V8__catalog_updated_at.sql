-- V8: marca de actualizacion para el feed de exportacion del catalogo (P0-1).
--
-- ms-04 publica GET /api/v1/admin/catalog/export y delega en ms-03 los agregados ACTIVITIES y
-- EVENTS. El feed es incremental por updated_at, pero actividad/evento no tenian esa columna (ni
-- created_at), asi que sin esta migracion no hay forma de ordenar ni de paginar de forma estable.
--
-- Backfill: las filas existentes toman now(). El feed no tiene historial previo, asi que ese
-- valor solo provoca una primera exportacion completa; a partir de ahi el delta es correcto.

ALTER TABLE actividad ADD COLUMN IF NOT EXISTS updated_at timestamptz;
ALTER TABLE evento   ADD COLUMN IF NOT EXISTS updated_at timestamptz;

UPDATE actividad SET updated_at = now() WHERE updated_at IS NULL;
UPDATE evento   SET updated_at = now() WHERE updated_at IS NULL;

-- DEFAULT now(): una escritura de una instancia antigua durante un despliegue progresivo no debe
-- romper por NOT NULL. La aplicacion reescribe la columna en cada save.
ALTER TABLE actividad ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE evento   ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE actividad ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE evento   ALTER COLUMN updated_at SET NOT NULL;

-- El export pagina con ORDER BY updated_at, id: el indice compuesto evita el sort en memoria.
CREATE INDEX IF NOT EXISTS actividad_updated_at_idx ON actividad (updated_at, id);
CREATE INDEX IF NOT EXISTS evento_updated_at_idx ON evento (updated_at, id);
