// loadtests/k6/flows.js
// Flujo de negocio completo de un turista: login, lectura de actividad, creacion de resena
// y creacion de reserva (ms-03). SPEC P1-8.
//
// Cada VU trabaja con datos propios y unicos: su propio turista (registrado en setup), su
// propia actividad y una resena/reserva por VU. Asi se respetan las reglas de negocio reales:
//  - una resena por turista e item (CreateReviewUseCase: "The user already reviewed this item.");
//  - maximo 5 reservas activas por turista (MAX_ACTIVE_RESERVATIONS_PER_TOURIST = 5).
// Por eso el escenario usa `per-vu-iterations` con 1 iteracion por VU.
//
// Si el catalogo ya tiene actividades PUBLICADAS con fechas futuras y capacidad, el script las
// reutiliza; si no (ms-03 no trae datos semilla), crea categoria, zona, sitio y actividades con
// el token de administrador. Deja datos de prueba en el stack local (nombres con timestamp).
//
// Variables de entorno:
//   AUTH_BASE_URL  base de ms-01 (default http://ms-01:8081)
//   BASE_URL       base de ms-03 (default http://ms-03:8082)
//   SITES_BASE_URL base de ms-04 (default http://ms-04:8080)
//   ADMIN_USER / ADMIN_PASSWORD (o USER / PASSWORD como alias)
//   VUS (numero de turistas/actividades a crear, default 10), ITERATIONS (default 1)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const SCENARIO = (__ENV.SCENARIO || 'load').toLowerCase();
const VUS = Number(__ENV.VUS || 10);
const ITERATIONS = Number(__ENV.ITERATIONS || 1);

const AUTH_BASE_URL = __ENV.AUTH_BASE_URL || 'http://ms-01:8081';
const BASE_URL = __ENV.BASE_URL || 'http://ms-03:8082';
const SITES_BASE_URL = __ENV.SITES_BASE_URL || 'http://ms-04:8080';
const ADMIN_USER = __ENV.ADMIN_USER || __ENV.USER || 'admin@smxplore.local';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || __ENV.PASSWORD || 'ChangeMe123!';
const PASSWORD_TOURIST = 'PerfTest12345';
const MAX_PAGE_SIZE = 100;

// Exito de la secuencia de negocio completa por VU.
const businessOk = new Rate('flow_business_ok');

function buildOptions() {
  const tags = { scenario: SCENARIO === 'smoke' ? 'smoke' : 'load' };
  return {
    discardResponseBodies: false,
    // setup() crea catalogo y un turista por VU: puede tardar mas que el limite por defecto.
    setupTimeout: '5m',
    scenarios: {
      flows: {
        executor: 'per-vu-iterations',
        vus: VUS,
        iterations: ITERATIONS,
        maxDuration: '3m',
        tags: tags,
      },
    },
    thresholds: {
      // Thresholds mas laxos que los de lectura: hay escrituras y eventos de dominio detras.
      http_req_failed: ['rate<0.02'],
      'http_req_duration': ['p(95)<1500'],
      checks: ['rate>0.95'],
      flow_business_ok: ['rate>0.95'],
    },
  };
}

export const options = buildOptions();

function parseBody(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function jsonHeaders(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
}

function login(email, password, endpointTag) {
  const res = http.post(
    `${AUTH_BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: email, password: password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: endpointTag || 'login' } },
  );
  const body = parseBody(res);
  if (res.status !== 200 || !body || !body.accessToken) {
    throw new Error(`Login fallo para ${email}: HTTP ${res.status} ${res.body}`);
  }
  if (body.mfaRequired) {
    throw new Error(`El usuario ${email} exige MFA; usa una cuenta sin MFA para las pruebas de carga.`);
  }
  return body.accessToken;
}

function isoIn(offsetMs) {
  // ISO-8601 UTC con milisegundos; Jackson/Instant lo acepta sin configuracion extra.
  return new Date(Date.now() + offsetMs).toISOString();
}

function registerTourist(email, stamp) {
  const res = http.post(
    `${AUTH_BASE_URL}/api/v1/auth/register/turista`,
    JSON.stringify({
      fullName: `Perf Turista ${stamp}`,
      email: email,
      phone: '3001234567',
      password: PASSWORD_TOURIST,
      dniType: 'CC',
      dniNumber: String(stamp).slice(-9),
      preferredLanguage: 'es',
      birthDate: '1990-01-01',
      nationality: 'CO',
    }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'register_tourist' } },
  );
  const body = parseBody(res);
  if ((res.status !== 201 && res.status !== 200) || !body || !body.id) {
    throw new Error(`No se pudo registrar el turista ${email}: HTTP ${res.status} ${res.body}`);
  }
  return body.id;
}

function discoverActivities() {
  const res = http.get(`${BASE_URL}/api/v1/activities?page=0&size=${MAX_PAGE_SIZE}`, {
    tags: { endpoint: 'discover_activities' },
  });
  const body = parseBody(res);
  if (!body || !Array.isArray(body.content)) {
    return [];
  }
  const now = Date.now();
  return body.content
    .filter((item) => item && item.id && item.status === 'PUBLISHED')
    .filter((item) => item.maxCapacity === null || item.maxCapacity === undefined || item.maxCapacity >= 1)
    .filter((item) => item.startsAt && item.endsAt)
    // La reserva exige startsAt y endsAt en el futuro (CreateReservationUseCase).
    .filter((item) => Date.parse(item.startsAt) > now && Date.parse(item.endsAt) > now)
    .map((item) => ({ id: item.id, startsAt: item.startsAt, endsAt: item.endsAt }));
}

function postJson(url, token, payload, endpointTag) {
  return http.post(url, JSON.stringify(payload), {
    headers: jsonHeaders(token),
    tags: { endpoint: endpointTag },
  });
}

function requireField(res, field, context) {
  const body = parseBody(res);
  if ((res.status !== 200 && res.status !== 201) || !body || !body[field]) {
    throw new Error(`${context}: HTTP ${res.status} ${res.body}`);
  }
  return body[field];
}

function createVenueAndActivities(adminToken, stamp, count, startsAt, endsAt) {
  const category = postJson(
    `${SITES_BASE_URL}/api/v1/categories`,
    adminToken,
    { name: `Perf categoria ${stamp}`, scope: 'ACTIVITY' },
    'create_category',
  );
  const categoryId = requireField(category, 'categoryId', 'No se pudo crear la categoria de prueba');

  const zone = postJson(
    `${SITES_BASE_URL}/api/v1/zones`,
    adminToken,
    { name: `Perf zona ${stamp}`, type: 'URBANA', description: 'zona de carga P1-8' },
    'create_zone',
  );
  const zoneId = requireField(zone, 'zoneId', 'No se pudo crear la zona de prueba');

  const site = postJson(
    `${SITES_BASE_URL}/api/v1/sites`,
    adminToken,
    {
      name: `Perf sitio ${stamp}`,
      description: 'sitio de carga P1-8',
      categoryId: categoryId,
      zoneId: zoneId,
      latitude: 11.2408,
      longitude: -74.1990,
      maxCapacity: 50,
    },
    'create_site',
  );
  const siteId = requireField(site, 'siteId', 'No se pudo crear el sitio de prueba');

  const created = [];
  for (let i = 0; i < count; i += 1) {
    const activity = postJson(
      `${BASE_URL}/api/v1/activities`,
      adminToken,
      {
        siteId: siteId,
        name: `Perf actividad ${stamp}-${i}`,
        category: 'AVENTURA',
        price: 25.0,
        maxCapacity: 5,
        durationMinutes: 120,
        startsAt: startsAt,
        endsAt: endsAt,
        description: 'actividad de carga P1-8',
      },
      'create_activity',
    );
    const activityId = requireField(activity, 'id', 'No se pudo crear la actividad de prueba');

    // Una actividad nace en DRAFT: sin publicarla la resena y la reserva fallan con 400.
    const published = http.patch(
      `${BASE_URL}/api/v1/activities/${activityId}/status`,
      JSON.stringify({ status: 'PUBLISHED' }),
      { headers: jsonHeaders(adminToken), tags: { endpoint: 'publish_activity' } },
    );
    const publishedBody = parseBody(published);
    if (published.status !== 200 || !publishedBody || publishedBody.status !== 'PUBLISHED') {
      throw new Error(`No se pudo publicar la actividad ${activityId}: HTTP ${published.status}`);
    }
    created.push({ id: activityId, startsAt: startsAt, endsAt: endsAt });
  }
  return created;
}

export function setup() {
  const stamp = Date.now();
  const adminToken = login(ADMIN_USER, ADMIN_PASSWORD, 'login_admin');

  // 1) Catalogo: reutiliza actividades validas o crea las que falten (ms-03 no trae seed).
  let activities = discoverActivities().slice(0, VUS);
  if (activities.length < VUS) {
    const startsAt = isoIn(3 * 24 * 60 * 60 * 1000); // +3 dias
    const endsAt = isoIn(3 * 24 * 60 * 60 * 1000 + 2 * 60 * 60 * 1000); // +3 dias y 2 horas
    const created = createVenueAndActivities(adminToken, stamp, VUS - activities.length, startsAt, endsAt);
    activities = activities.concat(created);
  }
  if (activities.length === 0) {
    throw new Error('No hay actividades PUBLICADAS con fechas futuras y no se pudieron crear.');
  }

  // 2) Un turista propio por VU (email unico) para que resena y reserva sean validas.
  const tourists = [];
  for (let i = 0; i < VUS; i += 1) {
    const email = `perf-flows-${stamp}-${i}@smxplore.local`;
    const userId = registerTourist(email, `${stamp}${i}`);
    const token = login(email, PASSWORD_TOURIST, 'login_tourist');
    tourists.push({ userId: userId, token: token, email: email });
  }

  console.log(`flows: ${activities.length} actividades y ${tourists.length} turistas listos (stamp ${stamp}).`);
  return { activities: activities, tourists: tourists };
}

export default function (data) {
  const index = ((__VU - 1) % data.activities.length + data.activities.length) % data.activities.length;
  const touristIndex = ((__VU - 1) % data.tourists.length + data.tourists.length) % data.tourists.length;
  const activity = data.activities[index];
  const tourist = data.tourists[touristIndex];
  const headers = { Authorization: `Bearer ${tourist.token}` };

  // Solo la primera iteracion de cada VU escribe: respeta resena unica y el tope de reservas.
  if (__ITER > 0) {
    http.get(`${BASE_URL}/api/v1/activities/${activity.id}`, {
      headers: headers,
      tags: { endpoint: 'activity_read' },
    });
    sleep(0.2);
    return;
  }

  const activityRes = http.get(`${BASE_URL}/api/v1/activities/${activity.id}`, {
    headers: headers,
    tags: { endpoint: 'activity_read' },
  });
  check(activityRes, { 'actividad visible 200': (r) => r.status === 200 });

  const reviewRes = http.post(
    `${BASE_URL}/api/v1/reviews`,
    JSON.stringify({
      itemType: 'ACTIVITY',
      itemId: activity.id,
      rating: 5,
      comment: `Carga P1-8 VU ${__VU} ${Date.now()}`,
    }),
    { headers: jsonHeaders(tourist.token), tags: { endpoint: 'review_create' } },
  );
  check(reviewRes, {
    'resena creada 201': (r) => r.status === 201,
  });

  const reservationRes = http.post(
    `${BASE_URL}/api/v1/reservations`,
    JSON.stringify({
      touristId: tourist.userId,
      itemType: 'ACTIVITY',
      itemId: activity.id,
      numberOfPeople: 1,
      paymentMethod: 'CARD',
      bookedFor: activity.startsAt,
    }),
    { headers: jsonHeaders(tourist.token), tags: { endpoint: 'reservation_create' } },
  );
  const reservationBody = parseBody(reservationRes);
  const reservationId = reservationBody && reservationBody.id ? reservationBody.id : null;
  const reservationOk = check(reservationRes, {
    'reserva creada 201': (r) => r.status === 201,
    'reserva devuelve id': () => reservationId !== null,
  });

  if (reservationId) {
    const readRes = http.get(`${BASE_URL}/api/v1/reservations/${reservationId}`, {
      headers: headers,
      tags: { endpoint: 'reservation_read' },
    });
    check(readRes, { 'reserva legible 200': (r) => r.status === 200 });
  }

  const reviewBody = parseBody(reviewRes);
  businessOk.add(reviewRes.status === 201 && reservationOk && Boolean(reviewBody && reviewBody.id));

  sleep(0.2);
}
