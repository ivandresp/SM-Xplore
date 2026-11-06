// ---------------------------------------------------------------------------
// Categorias
// ---------------------------------------------------------------------------
MERGE (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111101'})
SET c.name = 'Naturaleza', c.scope = 'GENERAL', c.parentId = null, c.updatedAt = datetime() - duration('P30D');

MERGE (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111102'})
SET c.name = 'Playas', c.scope = 'SITE', c.parentId = '11111111-1111-1111-1111-111111111101', c.updatedAt = datetime() - duration('P30D');

MERGE (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111103'})
SET c.name = 'Ecoturismo', c.scope = 'ACTIVITY', c.parentId = '11111111-1111-1111-1111-111111111101', c.updatedAt = datetime() - duration('P30D');

MERGE (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111104'})
SET c.name = 'Aventura', c.scope = 'ACTIVITY', c.parentId = '11111111-1111-1111-1111-111111111101', c.updatedAt = datetime() - duration('P30D');

MERGE (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111105'})
SET c.name = 'Cultura', c.scope = 'GENERAL', c.parentId = null, c.updatedAt = datetime() - duration('P30D');

MERGE (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111106'})
SET c.name = 'Música', c.scope = 'EVENT', c.parentId = '11111111-1111-1111-1111-111111111105', c.updatedAt = datetime() - duration('P30D');

MERGE (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111107'})
SET c.name = 'Gastronomía', c.scope = 'SERVICE', c.parentId = null, c.updatedAt = datetime() - duration('P30D');

MERGE (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111108'})
SET c.name = 'Hospedaje', c.scope = 'SERVICE', c.parentId = null, c.updatedAt = datetime() - duration('P30D');

MERGE (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111109'})
SET c.name = 'Servicios turísticos', c.scope = 'SERVICE', c.parentId = null, c.updatedAt = datetime() - duration('P30D');

// ---------------------------------------------------------------------------
// Jerarquia de categorias
// ---------------------------------------------------------------------------
MATCH (child:Categoria {categoriaId: '11111111-1111-1111-1111-111111111102'}),
      (parent:Categoria {categoriaId: '11111111-1111-1111-1111-111111111101'})
MERGE (child)-[:PERTENECE_A]->(parent);

MATCH (child:Categoria {categoriaId: '11111111-1111-1111-1111-111111111103'}),
      (parent:Categoria {categoriaId: '11111111-1111-1111-1111-111111111101'})
MERGE (child)-[:PERTENECE_A]->(parent);

MATCH (child:Categoria {categoriaId: '11111111-1111-1111-1111-111111111104'}),
      (parent:Categoria {categoriaId: '11111111-1111-1111-1111-111111111101'})
MERGE (child)-[:PERTENECE_A]->(parent);

MATCH (child:Categoria {categoriaId: '11111111-1111-1111-1111-111111111106'}),
      (parent:Categoria {categoriaId: '11111111-1111-1111-1111-111111111105'})
MERGE (child)-[:PERTENECE_A]->(parent);

// ---------------------------------------------------------------------------
// Sitios
// ---------------------------------------------------------------------------
MERGE (s:CatalogItem:Sitio {siteId: '22222222-2222-2222-2222-222222222201'})
SET s.itemId = '22222222-2222-2222-2222-222222222201',
    s.itemType = 'SITE',
    s.siteId = '22222222-2222-2222-2222-222222222201',
    s.name = 'Playa Rosa',
    s.categoryId = '11111111-1111-1111-1111-111111111102',
    s.latitude = 10.3910,
    s.longitude = -75.4790,
    s.location = point({latitude: 10.3910, longitude: -75.4790}),
    s.status = 'PUBLISHED',
    s.updatedAt = datetime() - duration('P3D');

MERGE (s:CatalogItem:Sitio {siteId: '22222222-2222-2222-2222-222222222202'})
SET s.itemId = '22222222-2222-2222-2222-222222222202',
    s.itemType = 'SITE',
    s.siteId = '22222222-2222-2222-2222-222222222202',
    s.name = 'Centro Histórico',
    s.categoryId = '11111111-1111-1111-1111-111111111105',
    s.latitude = 10.4236,
    s.longitude = -75.5474,
    s.location = point({latitude: 10.4236, longitude: -75.5474}),
    s.status = 'PUBLISHED',
    s.updatedAt = datetime() - duration('P5D');

MERGE (s:CatalogItem:Sitio {siteId: '22222222-2222-2222-2222-222222222203'})
SET s.itemId = '22222222-2222-2222-2222-222222222203',
    s.itemType = 'SITE',
    s.siteId = '22222222-2222-2222-2222-222222222203',
    s.name = 'Sierra Verde',
    s.categoryId = '11111111-1111-1111-1111-111111111103',
    s.latitude = 10.3500,
    s.longitude = -75.6000,
    s.location = point({latitude: 10.3500, longitude: -75.6000}),
    s.status = 'PUBLISHED',
    s.updatedAt = datetime() - duration('P2D');

// ---------------------------------------------------------------------------
// Actividades
// ---------------------------------------------------------------------------
MERGE (a:CatalogItem:Actividad {activityId: '33333333-3333-3333-3333-333333333301'})
SET a.itemId = '33333333-3333-3333-3333-333333333301',
    a.itemType = 'ACTIVITY',
    a.activityId = '33333333-3333-3333-3333-333333333301',
    a.name = 'Snorkel en Playa Rosa',
    a.categoryId = '11111111-1111-1111-1111-111111111103',
    a.siteId = '22222222-2222-2222-2222-222222222201',
    a.latitude = 10.3910,
    a.longitude = -75.4790,
    a.price = 80000,
    a.status = 'PUBLISHED',
    a.ratingAvg = 4.6,
    a.ratingCount = 45,
    a.lastReviewAt = datetime() - duration('P2D'),
    a.updatedAt = datetime() - duration('P4D');

MERGE (a:CatalogItem:Actividad {activityId: '33333333-3333-3333-3333-333333333302'})
SET a.itemId = '33333333-3333-3333-3333-333333333302',
    a.itemType = 'ACTIVITY',
    a.activityId = '33333333-3333-3333-3333-333333333302',
    a.name = 'Caminata ecológica',
    a.categoryId = '11111111-1111-1111-1111-111111111104',
    a.siteId = '22222222-2222-2222-2222-222222222203',
    a.latitude = 10.3500,
    a.longitude = -75.6000,
    a.price = 45000,
    a.status = 'PUBLISHED',
    a.ratingAvg = 4.8,
    a.ratingCount = 30,
    a.lastReviewAt = datetime() - duration('P1D'),
    a.updatedAt = datetime() - duration('P1D');

MERGE (a:CatalogItem:Actividad {activityId: '33333333-3333-3333-3333-333333333303'})
SET a.itemId = '33333333-3333-3333-3333-333333333303',
    a.itemType = 'ACTIVITY',
    a.activityId = '33333333-3333-3333-3333-333333333303',
    a.name = 'Tour gastronómico',
    a.categoryId = '11111111-1111-1111-1111-111111111107',
    a.siteId = '22222222-2222-2222-2222-222222222202',
    a.latitude = 10.4236,
    a.longitude = -75.5474,
    a.price = 60000,
    a.status = 'PUBLISHED',
    a.ratingAvg = 4.4,
    a.ratingCount = 60,
    a.lastReviewAt = datetime() - duration('P3D'),
    a.updatedAt = datetime() - duration('P6D');

MERGE (a:CatalogItem:Actividad {activityId: '33333333-3333-3333-3333-333333333304'})
SET a.itemId = '33333333-3333-3333-3333-333333333304',
    a.itemType = 'ACTIVITY',
    a.activityId = '33333333-3333-3333-3333-333333333304',
    a.name = 'Clase de baile',
    a.categoryId = '11111111-1111-1111-1111-111111111105',
    a.siteId = '22222222-2222-2222-2222-222222222202',
    a.latitude = 10.4236,
    a.longitude = -75.5474,
    a.price = 30000,
    a.status = 'PUBLISHED',
    a.ratingAvg = 4.2,
    a.ratingCount = 15,
    a.lastReviewAt = datetime() - duration('P8D'),
    a.updatedAt = datetime() - duration('P10D');

// ---------------------------------------------------------------------------
// Eventos
// ---------------------------------------------------------------------------
MERGE (e:CatalogItem:Evento {eventId: '44444444-4444-4444-4444-444444444401'})
SET e.itemId = '44444444-4444-4444-4444-444444444401',
    e.itemType = 'EVENT',
    e.eventId = '44444444-4444-4444-4444-444444444401',
    e.name = 'Festival del Mar',
    e.categoryId = '11111111-1111-1111-1111-111111111106',
    e.siteId = '22222222-2222-2222-2222-222222222201',
    e.latitude = 10.3910,
    e.longitude = -75.4790,
    e.price = 100000,
    e.startsAt = datetime() + duration('P7D'),
    e.endsAt = datetime() + duration('P7DT6H'),
    e.status = 'PUBLISHED',
    e.ratingAvg = 4.7,
    e.ratingCount = 120,
    e.lastReviewAt = datetime() - duration('P2D'),
    e.updatedAt = datetime() - duration('P2D');

MERGE (e:CatalogItem:Evento {eventId: '44444444-4444-4444-4444-444444444402'})
SET e.itemId = '44444444-4444-4444-4444-444444444402',
    e.itemType = 'EVENT',
    e.eventId = '44444444-4444-4444-4444-444444444402',
    e.name = 'Noche de Jazz',
    e.categoryId = '11111111-1111-1111-1111-111111111106',
    e.siteId = '22222222-2222-2222-2222-222222222202',
    e.latitude = 10.4236,
    e.longitude = -75.5474,
    e.price = 70000,
    e.startsAt = datetime() + duration('P3D'),
    e.endsAt = datetime() + duration('P3DT4H'),
    e.status = 'PUBLISHED',
    e.ratingAvg = 4.5,
    e.ratingCount = 80,
    e.lastReviewAt = datetime() - duration('P1D'),
    e.updatedAt = datetime() - duration('P1D');

MERGE (e:CatalogItem:Evento {eventId: '44444444-4444-4444-4444-444444444403'})
SET e.itemId = '44444444-4444-4444-4444-444444444403',
    e.itemType = 'EVENT',
    e.eventId = '44444444-4444-4444-4444-444444444403',
    e.name = 'Feria Gastronómica',
    e.categoryId = '11111111-1111-1111-1111-111111111107',
    e.siteId = '22222222-2222-2222-2222-222222222202',
    e.latitude = 10.4236,
    e.longitude = -75.5474,
    e.price = 20000,
    e.startsAt = datetime() + duration('P10D'),
    e.endsAt = datetime() + duration('P12D'),
    e.status = 'PUBLISHED',
    e.ratingAvg = 4.3,
    e.ratingCount = 40,
    e.lastReviewAt = datetime() - duration('P4D'),
    e.updatedAt = datetime() - duration('P4D');

MERGE (e:CatalogItem:Evento {eventId: '44444444-4444-4444-4444-444444444404'})
SET e.itemId = '44444444-4444-4444-4444-444444444404',
    e.itemType = 'EVENT',
    e.eventId = '44444444-4444-4444-4444-444444444404',
    e.name = 'Teatro al aire libre',
    e.categoryId = '11111111-1111-1111-1111-111111111105',
    e.siteId = '22222222-2222-2222-2222-222222222202',
    e.latitude = 10.4236,
    e.longitude = -75.5474,
    e.price = 25000,
    e.startsAt = datetime() + duration('P5D'),
    e.endsAt = datetime() + duration('P5DT3H'),
    e.status = 'PUBLISHED',
    e.ratingAvg = 4.1,
    e.ratingCount = 10,
    e.lastReviewAt = datetime() - duration('P9D'),
    e.updatedAt = datetime() - duration('P9D');

MERGE (e:CatalogItem:Evento {eventId: '44444444-4444-4444-4444-444444444405'})
SET e.itemId = '44444444-4444-4444-4444-444444444405',
    e.itemType = 'EVENT',
    e.eventId = '44444444-4444-4444-4444-444444444405',
    e.name = 'Evento pasado',
    e.categoryId = '11111111-1111-1111-1111-111111111106',
    e.siteId = '22222222-2222-2222-2222-222222222201',
    e.latitude = 10.3910,
    e.longitude = -75.4790,
    e.price = 50000,
    e.startsAt = datetime() - duration('P10D'),
    e.endsAt = datetime() - duration('P9D'),
    e.status = 'PUBLISHED',
    e.ratingAvg = 4.9,
    e.ratingCount = 200,
    e.lastReviewAt = datetime() - duration('P10D'),
    e.updatedAt = datetime() - duration('P10D');

// ---------------------------------------------------------------------------
// Servicios
// ---------------------------------------------------------------------------
MERGE (v:CatalogItem:Servicio {serviceId: '55555555-5555-5555-5555-555555555501'})
SET v.itemId = '55555555-5555-5555-5555-555555555501',
    v.itemType = 'SERVICE',
    v.serviceId = '55555555-5555-5555-5555-555555555501',
    v.name = 'Restaurante del Mar',
    v.type = 'RESTAURANT',
    v.categoryId = '11111111-1111-1111-1111-111111111107',
    v.siteId = '22222222-2222-2222-2222-222222222201',
    v.latitude = 10.3910,
    v.longitude = -75.4790,
    v.location = point({latitude: 10.3910, longitude: -75.4790}),
    v.status = 'PUBLISHED',
    v.ratingAvg = 4.6,
    v.ratingCount = 90,
    v.lastReviewAt = datetime() - duration('P2D'),
    v.updatedAt = datetime() - duration('P3D');

MERGE (v:CatalogItem:Servicio {serviceId: '55555555-5555-5555-5555-555555555502'})
SET v.itemId = '55555555-5555-5555-5555-555555555502',
    v.itemType = 'SERVICE',
    v.serviceId = '55555555-5555-5555-5555-555555555502',
    v.name = 'Hotel Sierra',
    v.type = 'HOTEL',
    v.categoryId = '11111111-1111-1111-1111-111111111108',
    v.siteId = '22222222-2222-2222-2222-222222222203',
    v.latitude = 10.3500,
    v.longitude = -75.6000,
    v.location = point({latitude: 10.3500, longitude: -75.6000}),
    v.status = 'PUBLISHED',
    v.ratingAvg = 4.5,
    v.ratingCount = 70,
    v.lastReviewAt = datetime() - duration('P5D'),
    v.updatedAt = datetime() - duration('P6D');

MERGE (v:CatalogItem:Servicio {serviceId: '55555555-5555-5555-5555-555555555503'})
SET v.itemId = '55555555-5555-5555-5555-555555555503',
    v.itemType = 'SERVICE',
    v.serviceId = '55555555-5555-5555-5555-555555555503',
    v.name = 'Guía local',
    v.type = 'GUIDE',
    v.categoryId = '11111111-1111-1111-1111-111111111105',
    v.siteId = '22222222-2222-2222-2222-222222222202',
    v.latitude = 10.4236,
    v.longitude = -75.5474,
    v.location = point({latitude: 10.4236, longitude: -75.5474}),
    v.status = 'PUBLISHED',
    v.ratingAvg = 4.9,
    v.ratingCount = 25,
    v.lastReviewAt = datetime() - duration('P1D'),
    v.updatedAt = datetime() - duration('P2D');

MERGE (v:CatalogItem:Servicio {serviceId: '55555555-5555-5555-5555-555555555504'})
SET v.itemId = '55555555-5555-5555-5555-555555555504',
    v.itemType = 'SERVICE',
    v.serviceId = '55555555-5555-5555-5555-555555555504',
    v.name = 'Transporte turístico',
    v.type = 'TRANSPORT',
    v.categoryId = '11111111-1111-1111-1111-111111111109',
    v.siteId = '22222222-2222-2222-2222-222222222202',
    v.latitude = 10.4236,
    v.longitude = -75.5474,
    v.location = point({latitude: 10.4236, longitude: -75.5474}),
    v.status = 'PUBLISHED',
    v.ratingAvg = 4.0,
    v.ratingCount = 12,
    v.lastReviewAt = datetime() - duration('P7D'),
    v.updatedAt = datetime() - duration('P8D');

// ---------------------------------------------------------------------------
// ES_DE_TIPO
// ---------------------------------------------------------------------------
MATCH (i:CatalogItem {itemId: '22222222-2222-2222-2222-222222222201'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111102'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '22222222-2222-2222-2222-222222222202'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111105'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '22222222-2222-2222-2222-222222222203'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111103'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '33333333-3333-3333-3333-333333333301'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111103'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '33333333-3333-3333-3333-333333333302'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111104'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '33333333-3333-3333-3333-333333333303'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111107'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '33333333-3333-3333-3333-333333333304'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111105'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '44444444-4444-4444-4444-444444444401'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111106'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '44444444-4444-4444-4444-444444444402'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111106'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '44444444-4444-4444-4444-444444444403'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111107'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '44444444-4444-4444-4444-444444444404'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111105'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '44444444-4444-4444-4444-444444444405'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111106'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '55555555-5555-5555-5555-555555555501'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111107'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '55555555-5555-5555-5555-555555555502'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111108'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '55555555-5555-5555-5555-555555555503'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111105'})
MERGE (i)-[:ES_DE_TIPO]->(c);

MATCH (i:CatalogItem {itemId: '55555555-5555-5555-5555-555555555504'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111109'})
MERGE (i)-[:ES_DE_TIPO]->(c);

// ---------------------------------------------------------------------------
// OCURRE_EN / UBICADO_EN
// ---------------------------------------------------------------------------
MATCH (i:CatalogItem {itemId: '33333333-3333-3333-3333-333333333301'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222201'})
MERGE (i)-[:OCURRE_EN]->(s);

MATCH (i:CatalogItem {itemId: '33333333-3333-3333-3333-333333333302'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222203'})
MERGE (i)-[:OCURRE_EN]->(s);

MATCH (i:CatalogItem {itemId: '33333333-3333-3333-3333-333333333303'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222202'})
MERGE (i)-[:OCURRE_EN]->(s);

MATCH (i:CatalogItem {itemId: '33333333-3333-3333-3333-333333333304'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222202'})
MERGE (i)-[:OCURRE_EN]->(s);

MATCH (i:CatalogItem {itemId: '44444444-4444-4444-4444-444444444401'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222201'})
MERGE (i)-[:OCURRE_EN]->(s);

MATCH (i:CatalogItem {itemId: '44444444-4444-4444-4444-444444444402'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222202'})
MERGE (i)-[:OCURRE_EN]->(s);

MATCH (i:CatalogItem {itemId: '44444444-4444-4444-4444-444444444403'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222202'})
MERGE (i)-[:OCURRE_EN]->(s);

MATCH (i:CatalogItem {itemId: '44444444-4444-4444-4444-444444444404'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222202'})
MERGE (i)-[:OCURRE_EN]->(s);

MATCH (i:CatalogItem {itemId: '44444444-4444-4444-4444-444444444405'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222201'})
MERGE (i)-[:OCURRE_EN]->(s);

MATCH (v:Servicio {serviceId: '55555555-5555-5555-5555-555555555501'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222201'})
MERGE (v)-[:UBICADO_EN]->(s);

MATCH (v:Servicio {serviceId: '55555555-5555-5555-5555-555555555502'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222203'})
MERGE (v)-[:UBICADO_EN]->(s);

MATCH (v:Servicio {serviceId: '55555555-5555-5555-5555-555555555503'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222202'})
MERGE (v)-[:UBICADO_EN]->(s);

MATCH (v:Servicio {serviceId: '55555555-5555-5555-5555-555555555504'}),
      (s:Sitio {siteId: '22222222-2222-2222-2222-222222222202'})
MERGE (v)-[:UBICADO_EN]->(s);

// ---------------------------------------------------------------------------
// Turistas
// ---------------------------------------------------------------------------
MERGE (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666601'})
SET t.language = 'es', t.updatedAt = datetime() - duration('P1D');

MERGE (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666602'})
SET t.language = 'es', t.updatedAt = datetime() - duration('P2D');

MERGE (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666603'})
SET t.language = 'es', t.updatedAt = datetime() - duration('P1D');

// ---------------------------------------------------------------------------
// Preferencias explicitas
// ---------------------------------------------------------------------------
MATCH (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666601'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111106'})
MERGE (t)-[g:LE_GUSTA]->(c)
SET g.weight = 1.0, g.source = 'EXPLICIT', g.updatedAt = datetime() - duration('P10D');

MATCH (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666601'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111102'})
MERGE (t)-[g:LE_GUSTA]->(c)
SET g.weight = 1.0, g.source = 'EXPLICIT', g.updatedAt = datetime() - duration('P12D');

MATCH (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666601'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111107'})
MERGE (t)-[g:LE_GUSTA]->(c)
SET g.weight = 1.0, g.source = 'EXPLICIT', g.updatedAt = datetime() - duration('P20D');

MATCH (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666602'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111104'})
MERGE (t)-[g:LE_GUSTA]->(c)
SET g.weight = 1.0, g.source = 'EXPLICIT', g.updatedAt = datetime() - duration('P5D');

MATCH (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666602'}),
      (c:Categoria {categoriaId: '11111111-1111-1111-1111-111111111103'})
MERGE (t)-[g:LE_GUSTA]->(c)
SET g.weight = 1.0, g.source = 'EXPLICIT', g.updatedAt = datetime() - duration('P6D');

// ---------------------------------------------------------------------------
// Vistas
// ---------------------------------------------------------------------------
MATCH (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666601'}),
      (i:CatalogItem {itemId: '44444444-4444-4444-4444-444444444402'})
MERGE (t)-[v:VIO]->(i)
ON CREATE SET v.firstAt = datetime() - duration('P3D'), v.count = 0
SET v.lastAt = datetime() - duration('P1D'), v.count = coalesce(v.count, 0) + 1;

MATCH (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666601'}),
      (i:CatalogItem {itemId: '22222222-2222-2222-2222-222222222201'})
MERGE (t)-[v:VIO]->(i)
ON CREATE SET v.firstAt = datetime() - duration('P4D'), v.count = 0
SET v.lastAt = datetime() - duration('P2D'), v.count = coalesce(v.count, 0) + 1;

MATCH (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666602'}),
      (i:CatalogItem {itemId: '33333333-3333-3333-3333-333333333302'})
MERGE (t)-[v:VIO]->(i)
ON CREATE SET v.firstAt = datetime() - duration('P2D'), v.count = 0
SET v.lastAt = datetime() - duration('P1D'), v.count = coalesce(v.count, 0) + 1;

// ---------------------------------------------------------------------------
// Reseñas (sin RESERVO: solo senal de calidad y exclusion de ya reseñados)
// ---------------------------------------------------------------------------
MATCH (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666601'}),
      (i:CatalogItem {itemId: '44444444-4444-4444-4444-444444444402'})
MERGE (t)-[r:`RESEÑO` {reviewId: '77777777-7777-7777-7777-777777777701'}]->(i)
SET r.rating = 5, r.status = 'PUBLISHED', r.at = datetime() - duration('P2D'),
    r.updatedAt = datetime() - duration('P2D');

MATCH (t:TuristaProfile {touristId: '66666666-6666-6666-6666-666666666602'}),
      (i:CatalogItem {itemId: '33333333-3333-3333-3333-333333333301'})
MERGE (t)-[r:`RESEÑO` {reviewId: '77777777-7777-7777-7777-777777777702'}]->(i)
SET r.rating = 4, r.status = 'PUBLISHED', r.at = datetime() - duration('P3D'),
    r.updatedAt = datetime() - duration('P3D');
