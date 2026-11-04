CREATE CONSTRAINT turista_id IF NOT EXISTS
FOR (t:TuristaProfile) REQUIRE t.touristId IS UNIQUE;

CREATE CONSTRAINT categoria_id IF NOT EXISTS
FOR (c:Categoria) REQUIRE c.categoriaId IS UNIQUE;

CREATE CONSTRAINT catalog_item_id IF NOT EXISTS
FOR (i:CatalogItem) REQUIRE i.itemId IS UNIQUE;

CREATE CONSTRAINT sitio_id IF NOT EXISTS
FOR (s:Sitio) REQUIRE s.siteId IS UNIQUE;

CREATE CONSTRAINT actividad_id IF NOT EXISTS
FOR (a:Actividad) REQUIRE a.activityId IS UNIQUE;

CREATE CONSTRAINT evento_id IF NOT EXISTS
FOR (e:Evento) REQUIRE e.eventId IS UNIQUE;

CREATE CONSTRAINT servicio_id IF NOT EXISTS
FOR (v:Servicio) REQUIRE v.serviceId IS UNIQUE;

CREATE CONSTRAINT processed_event_id IF NOT EXISTS
FOR (p:ProcessedEvent) REQUIRE p.eventId IS UNIQUE;

CREATE INDEX catalog_item_status IF NOT EXISTS
FOR (i:CatalogItem) ON (i.status);

CREATE INDEX catalog_item_type IF NOT EXISTS
FOR (i:CatalogItem) ON (i.itemType);

CREATE INDEX catalog_item_site IF NOT EXISTS
FOR (i:CatalogItem) ON (i.siteId);

CREATE INDEX catalog_item_category IF NOT EXISTS
FOR (i:CatalogItem) ON (i.categoryId);

CREATE INDEX catalog_item_updated_at IF NOT EXISTS
FOR (i:CatalogItem) ON (i.updatedAt);

CREATE INDEX evento_starts_at IF NOT EXISTS
FOR (e:Evento) ON (e.startsAt);

CREATE INDEX evento_ends_at IF NOT EXISTS
FOR (e:Evento) ON (e.endsAt);

CREATE INDEX categoria_parent IF NOT EXISTS
FOR (c:Categoria) ON (c.parentId);

CREATE INDEX categoria_scope IF NOT EXISTS
FOR (c:Categoria) ON (c.scope);

CREATE INDEX processed_event_processed_at IF NOT EXISTS
FOR (p:ProcessedEvent) ON (p.processedAt);
