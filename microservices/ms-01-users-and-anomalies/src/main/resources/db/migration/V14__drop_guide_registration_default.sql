-- MS01-B8: el estado del alta de un guia se escribe siempre de forma explicita.
-- V13 dejo DEFAULT 'PENDIENTE' para poder rellenar las filas existentes en la misma migracion. Se retira: si una
-- version anterior de la aplicacion intentara insertar un guia sin la columna, debe fallar de forma visible por
-- NOT NULL en vez de crear altas PENDIENTES en silencio.
ALTER TABLE guide_profiles ALTER COLUMN registration_status DROP DEFAULT;
