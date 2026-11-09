// loadtests/k6/recommendations.js
// Carga de solo lectura contra GET /api/v1/recommendations (ms-05). SPEC P1-8.
//
// Escenarios:
//   SCENARIO=smoke (por defecto) -> 1 VU durante 10s, valida el contrato de la respuesta.
//   SCENARIO=load                -> rampa hasta VUS VUs, mide p95 del endpoint bajo carga.
//
// Variables de entorno (todas con default pensado para la red smxplore-stack):
//   BASE_URL        base de ms-05 (default http://ms-05:8080)
//   AUTH_BASE_URL   base de ms-01 para el login (default http://ms-01:8081)
//   ADMIN_USER / ADMIN_PASSWORD (o USER / PASSWORD como alias)
//   TOURIST_USER / TOURIST_PASSWORD (opcional: si faltan se registra un turista efimero)
//   VUS, DURATION, LIMIT, LAT, LNG
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const SCENARIO = (__ENV.SCENARIO || 'smoke').toLowerCase();
const VUS = Number(__ENV.VUS || 10);
const DURATION = __ENV.DURATION || '60s';

const BASE_URL = __ENV.BASE_URL || 'http://ms-05:8080';
const AUTH_BASE_URL = __ENV.AUTH_BASE_URL || 'http://ms-01:8081';
const ADMIN_USER = __ENV.ADMIN_USER || __ENV.USER || 'admin@smxplore.local';
// PLAT-23: sin password por defecto. El lanzador exige ADMIN_PASSWORD y se valida antes de
// hacer ninguna peticion; asi estas pruebas no se reutilizan contra un entorno con datos reales.
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || __ENV.PASSWORD || '';
const TOURIST_USER = __ENV.TOURIST_USER || '';
const TOURIST_PASSWORD = __ENV.TOURIST_PASSWORD || '';

const LIMIT = Number(__ENV.LIMIT || 20);
const LAT = Number(__ENV.LAT || 11.2408);
const LNG = Number(__ENV.LNG || -74.1990);
const PASSWORD_TOURIST = 'PerfTest12345';

// Tasa de respuestas con el cuerpo esperado (items[] y source conocidos).
const payloadOk = new Rate('recommendation_payload_ok');

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
    // SPEC P1-8: menos del 1% de fallos HTTP.
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.98'],
  };
  // SPEC P1-8: p95 del endpoint y del escenario load por debajo de 600 ms. En smoke
  // no se exige latencia: la primera peticion paga el arranque en frio de Neo4j/cache
  // y con 15 muestras un solo pico desborda el percentil.
  if (SCENARIO === 'load') {
    thresholds['http_req_duration{endpoint:recommendations}'] = ['p(95)<600'];
    thresholds['http_req_duration{scenario:load}'] = ['p(95)<600'];
  }
  return { discardResponseBodies: false, scenarios, thresholds };
}

export const options = buildOptions();

function jsonHeaders(extra) {
  return Object.assign({ 'Content-Type': 'application/json' }, extra || {});
}

function parseBody(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function login(email, password, tag) {
  const res = http.post(
    `${AUTH_BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: email, password: password }),
    { headers: jsonHeaders(), tags: { endpoint: 'login', flow: tag } },
  );
  const ok = check(res, {
    'login responde 200': (r) => r.status === 200,
    'login devuelve accessToken': (r) => {
      const body = parseBody(r);
      return Boolean(body && body.accessToken);
    },
  });
  if (!ok) {
    throw new Error(`Login fallo para ${email}: HTTP ${res.status} ${res.body}`);
  }
  const body = parseBody(res);
  if (body.mfaRequired) {
    throw new Error(`El usuario ${email} exige MFA; usa una cuenta sin MFA para las pruebas de carga.`);
  }
  return body.accessToken;
}

function registerTourist(email) {
  const stamp = Date.now();
  const res = http.post(
    `${AUTH_BASE_URL}/api/v1/auth/register/turista`,
    JSON.stringify({
      fullName: `Perf Turista ${stamp}`,
      email: email,
      // Identidad unica por corrida: ms-01 rechaza email, telefono o DNI repetidos.
      phone: `3${String(stamp).slice(-9)}`,
      password: PASSWORD_TOURIST,
      dniType: 'CC',
      dniNumber: String(stamp).slice(-9),
      preferredLanguage: 'es',
      birthDate: '1990-01-01',
      nationality: 'CO',
    }),
    { headers: jsonHeaders(), tags: { endpoint: 'register', flow: 'tourist' } },
  );
  check(res, {
    'registro de turista 201': (r) => r.status === 201 || r.status === 200,
  });
  if (res.status !== 201 && res.status !== 200) {
    throw new Error(`No se pudo registrar el turista de prueba: HTTP ${res.status} ${res.body}`);
  }
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
  const adminToken = login(ADMIN_USER, ADMIN_PASSWORD, 'admin');

  let touristToken = '';
  let touristEmail = TOURIST_USER;
  if (TOURIST_USER) {
    touristToken = login(TOURIST_USER, TOURIST_PASSWORD, 'tourist');
  } else {
    touristEmail = `perf-rec-${Date.now()}-${Math.floor(Math.random() * 10000)}@smxplore.local`;
    registerTourist(touristEmail);
    touristToken = login(touristEmail, PASSWORD_TOURIST, 'tourist');
  }

  return { adminToken: adminToken, touristToken: touristToken, touristEmail: touristEmail };
}

export default function (data) {
  // 80% turista (camino real con perfil) y 20% admin (soporte), ambos permitidos por ms-05.
  const useTourist = Boolean(data.touristToken) && Math.random() < 0.8;
  const token = useTourist ? data.touristToken : data.adminToken;

  const res = http.get(
    `${BASE_URL}/api/v1/recommendations?limit=${LIMIT}&lat=${LAT}&lng=${LNG}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { endpoint: 'recommendations' },
    },
  );

  const body = parseBody(res);
  const items = body && Array.isArray(body.items) ? body.items : null;
  const ok = check(res, {
    'recomendaciones 200': (r) => r.status === 200,
    'recomendaciones devuelve items[]': () => items !== null,
    'recomendaciones trae source conocido': () =>
      Boolean(body && ['AFFINITY', 'QUALITY', 'NEARBY', 'FALLBACK', 'MIXED'].indexOf(body.source) >= 0),
  });
  payloadOk.add(ok);

  // Piensa como un cliente: no martillea el endpoint en bucle cerrado.
  sleep(0.3 + Math.random() * 0.7);
}

export function teardown(data) {
  // El turista efimero queda registrado en ms-01; no hay endpoint de borrado en el stack local.
  if (data.touristEmail && !TOURIST_USER) {
    console.log(`Turista efimero usado en la prueba: ${data.touristEmail}`);
  }
}
