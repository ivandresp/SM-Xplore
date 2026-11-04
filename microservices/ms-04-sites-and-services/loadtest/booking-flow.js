// Escenario de humo y carga para ms-04 (sitios, servicios y reservas).
//
// Uso:
//   k6 run -e BASE_URL=http://localhost:8084 -e TOKEN="$TOKEN" \
//          -e SERVICE_ID=<uuid> microservices/ms-04-sites-and-services/loadtest/booking-flow.js
//
// Variables de entorno:
//   BASE_URL        URL de la API (por defecto http://localhost:8084)
//   TOKEN           JWT con rol TURISTA (obligatorio)
//   SERVICE_ID      servicio publicado sobre el que se reserva (obligatorio)
//   SERVICE_IDS     lista separada por comas para repartir lecturas (opcional)
//   BOOKING_DATE    ISO-8601 del turno a reservar; por defecto mañana a las 15:00 UTC
//   PEOPLE          personas por reserva (1)
//   SKIP_BOOKING    "true" para medir solo lecturas y no consumir cupo
//   VUS             usuarios virtuales en la meseta (10)
//
// El servicio reservado debe tener cupo suficiente: cada iteración consume
// PEOPLE plazas del turno elegido. Reinicie el cupo o use SKIP_BOOKING=true
// cuando solo quiera medir latencia de catálogo y disponibilidad.

import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { randomBytes } from 'k6/crypto';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8084').replace(/\/$/, '');
const TOKEN = __ENV.TOKEN || '';
const SERVICE_ID = __ENV.SERVICE_ID || '';
const SERVICE_IDS = (__ENV.SERVICE_IDS || '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);
const PEOPLE = Number(__ENV.PEOPLE || 1);
const SKIP_BOOKING = (__ENV.SKIP_BOOKING || 'false').toLowerCase() === 'true';
const VUS = Number(__ENV.VUS || 10);

if (!TOKEN) {
  fail('Falta TOKEN: exporte un JWT con rol TURISTA en la variable de entorno TOKEN.');
}
if (!SERVICE_ID && SERVICE_IDS.length === 0) {
  fail('Falta SERVICE_ID (o SERVICE_IDS) con un servicio publicado.');
}

const CATALOG = SERVICE_IDS.length > 0 ? SERVICE_IDS : [SERVICE_ID];

function tomorrowAt(hourUtc = 15) {
  const date = new Date(Date.now() + 24 * 60 * 60 * 1000);
  date.setUTCHours(hourUtc, 0, 0, 0);
  return date.toISOString();
}

const BOOKED_FOR = __ENV.BOOKING_DATE || tomorrowAt();

// UUID v4 sin dependencias externas: k6 no trae crypto.randomUUID.
function uuidv4() {
  const bytes = new Uint8Array(randomBytes(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

const JSON_HEADERS = {
  Authorization: `Bearer ${TOKEN}`,
  'Content-Type': 'application/json',
  Accept: 'application/json',
};

export const options = {
  scenarios: {
    catalog_reads: {
      executor: 'ramping-vus',
      exec: 'readCatalog',
      startVUs: 0,
      stages: [
        { duration: '30s', target: VUS },
        { duration: '60s', target: VUS },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
    booking_flow: {
      executor: 'constant-vus',
      exec: 'book',
      vus: Math.max(1, Math.floor(VUS / 2)),
      duration: '75s',
      startTime: '20s',
      gracefulStop: '15s',
    },
  },
  thresholds: {
    'http_req_duration{expected_response:true}': ['p(95)<800'],
    'http_req_failed{expected_response:true}': ['rate<0.01'],
    'checks': ['rate>0.98'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const response = http.get(`${BASE_URL}/actuator/health`, {
    headers: { Accept: 'application/json' },
  });
  if (response.status !== 200) {
    fail(`La API no responde en ${BASE_URL}/actuator/health (status ${response.status}).`);
  }
  return { bookedFor: BOOKED_FOR, catalog: CATALOG };
}

export function readCatalog(data) {
  const serviceId = data.catalog[Math.floor(Math.random() * data.catalog.length)];
  const service = http.get(`${BASE_URL}/api/v1/services/${serviceId}`, { headers: JSON_HEADERS });
  check(service, {
    'servicio 200': (r) => r.status === 200,
    'servicio con id': (r) => r.status !== 200 || r.json('serviceId') !== undefined,
  });

  const availability = http.get(
    `${BASE_URL}/api/v1/reservations/availability?serviceId=${serviceId}&bookedFor=${encodeURIComponent(data.bookedFor)}&people=${PEOPLE}`,
    { headers: JSON_HEADERS },
  );
  check(availability, {
    'disponibilidad 200': (r) => r.status === 200,
    'disponibilidad evaluada': (r) => r.status !== 200 || r.json('available') !== undefined,
  });

  sleep(1);
}

export function book(data) {
  if (SKIP_BOOKING) {
    sleep(1);
    return;
  }
  const serviceId = data.catalog[Math.floor(Math.random() * data.catalog.length)];
  const payload = JSON.stringify({
    bookedFor: data.bookedFor,
    notes: 'carga k6',
    items: [{ serviceId, people: PEOPLE }],
  });
  const response = http.post(`${BASE_URL}/api/v1/reservations`, payload, {
    headers: { ...JSON_HEADERS, 'Idempotency-Key': uuidv4() },
  });

  // 409 por cupo agotado es un resultado válido del negocio, no un fallo de carga.
  check(response, {
    'reserva creada o sin cupo': (r) => r.status === 201 || r.status === 409,
    'reserva responde JSON': (r) => r.headers['Content-Type'] !== undefined,
  });

  if (response.status === 201) {
    const reservationId = response.json('reservationId');
    check(reservationId, { 'reserva con id': (value) => Boolean(value) });
  }

  sleep(1);
}
