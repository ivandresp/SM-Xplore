-- Guarda el updatedAt del sitio que se aplico al snapshot, para no aceptar un evento viejo
-- que llegue despues de uno nuevo.
ALTER TABLE evento ADD COLUMN IF NOT EXISTS sitio_actualizado_en TIMESTAMPTZ;
