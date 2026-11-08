// loadtests/k6/catalog.js
// Carga de solo lectura contra el catalogo publico: ms-04 /api/v1/sites (requiere JWT)
// y ms-03 /api/v1/activities + /api/v1/events (publicos, sin token). SPEC P1-8.
//
// Escenarios:
//   SCENARIO=smoke (por defecto) -> 1 VU durante 10s.
//   SCENARIO=load                -> rampa hasta VUS VUs y p95 < 300 ms por endpoint.
//
// Variables de entorno:
//   BASE_URL        base de ms-03 (default http://ms-03:8082)
//   SITES_BASE_URL  base de ms-04 (default http://ms-04:8080)
//   AUTH_BASE_URL   base de ms-01 para el login (default http://ms-01:8081)
//   ADMIN_USER / ADMIN_PASSWORD (o USER / PASSWORD como alias)
//   VUS, DURATION, PAGE_SIZE
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Gauge, Rate } from 'k6/metrics';

const SCENARIO = (__ENV.SCENARIO || 'smoke').toLowerCase();
const VUS = Number(__ENV.VUS || 10);
const DURATION = __ENV.DURATION || '60s';

const BASE_URL = __ENV.BASE_URL || 'http://ms-03:8082';
const SITES_BASE_URL = __ENV.SITES_BASE_URL || 'http://ms-04:8080';
const AUTH_BASE_URL = __ENV.AUTH_BASE_URL || 'http://ms-01:8081';
const ADMIN_USER = __ENV.ADMIN_USER || __ENV.USER || 'admin@smxplore.local';
// PLAT-23: sin password por defecto. El lanzador exige ADMIN_PASSWORD y se valida antes de
// hacer ninguna peticion; asi estas pruebas no se reutilizan contra un entorno con datos reales.
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || __ENV.PASSWORD || '';
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 20);

// Numero de elementos devueltos por cada endpoint y proporcion de paginas vacias.
const catalogItems = new Gauge('catalog_items');
const emptyPages = new Rate('catalog_empty_pages');

function buildOptions() {
  const scenarios =
    SCENARIO === 'load'
      ? {
          load: {
            executor: 'ramping-vus',
            startVus: 0,
            stages: [
              { duration: '30s', target: VUS },
              { duration: DURATION, target: VUS },
              { duration: '15s', target: 0 },
            ],
            tags: { scenario: 'load' },
          },
        }
      : {
          smoke: {
            executor: 'constant-vus',
            vus: 1,
            duration: '10s',
            tags: { scenario: 'smoke' },
          },
        };

  const thresholds = {
    // SPEC P1-8: error HTTP < 1%.
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.98'],
  };
  // SPEC P1-8: p95 < 300 ms en los listados del catalogo, solo en load. En smoke la
  // muestra es minima y el arranque en frio de cada servicio desborda el percentil.
  if (SCENARIO === 'load') {
    thresholds['http_req_duration{endpoint:sites}'] = ['p(95)<300'];
    thresholds['http_req_duration{endpoint:activities}'] = ['p(95)<300'];
    thresholds['http_req_duration{endpoint:events}'] = ['p(95)<300'];
    thresholds['http_req_duration{scenario:load}'] = ['p(95)<300'];
  }
  return { discardResponseBodies: false, scenarios, thresholds };
}

export const options = buildOptions();

function parseBody(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function login(email, password) {
  const res = http.post(
    `${AUTH_BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: email, password: password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'login' },
    },
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

function pageIsValid(res, label) {
  const body = parseBody(res);
  const content = body && Array.isArray(body.content) ? body.content : null;
  return check(res, {
    [`${label} 200`]: (r) => r.status === 200,
    [`${label} content[]`]: () => content !== null,
  }) && content !== null;
}

function recordPage(label, res) {
  const body = parseBody(res);
  const content = body && Array.isArray(body.content) ? body.content : null;
  const size = content === null ? 0 : content.length;
  catalogItems.add(size, { endpoint: label });
  emptyPages.add(size === 0 ? 1 : 0, { endpoint: label });
}

function requireAdminPassword() {
  if (!ADMIN_PASSWORD) {
    throw new Error(
      'Falta ADMIN_PASSWORD: exporta la contrasena del administrador del stack local. ' +
      'No hay valor por defecto (PLAT-23) ni se debe reutilizar una contrasena real.'
    );
  }
}

export function setup() {
  requireAdminPassword();
  // ms-04 protege /api/v1/sites con JWT (cualquier rol autenticado); el resto es publico.
  return { adminToken: login(ADMIN_USER, ADMIN_PASSWORD) };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.adminToken}` };

  const sites = http.get(
    `${SITES_BASE_URL}/api/v1/sites?page=0&size=${PAGE_SIZE}`,
    { headers: headers, tags: { endpoint: 'sites' } },
  );
  recordPage('sites', sites);
  pageIsValid(sites, 'sites');

  const activities = http.get(
    `${BASE_URL}/api/v1/activities?page=0&size=${PAGE_SIZE}`,
    { tags: { endpoint: 'activities' } },
  );
  recordPage('activities', activities);
  pageIsValid(activities, 'activities');

  const events = http.get(
    `${BASE_URL}/api/v1/events?page=0&size=${PAGE_SIZE}`,
    { tags: { endpoint: 'events' } },
  );
  recordPage('events', events);
  pageIsValid(events, 'events');

  sleep(0.2 + Math.random() * 0.4);
}
