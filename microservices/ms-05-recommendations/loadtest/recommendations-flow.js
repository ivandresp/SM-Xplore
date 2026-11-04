// Carga de lectura sobre ms-05: recomendaciones de un turista autenticado.
//
// Uso:
//   k6 run loadtest/recommendations-flow.js \
//     -e BASE_URL=http://localhost:8085 -e TOKEN=$TOKEN \
//     -e SITE_ID=<uuid opcional> -e LAT=10.39 -e LNG=-75.47
//
// Requiere un JWT de turista emitido por ms-01 (RS256). El script mide el camino completo:
// version del grafo en Redis -> cache Caffeine/Redis -> ranking en Neo4j.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8085';
const TOKEN = __ENV.TOKEN || '';
const SITE_ID = __ENV.SITE_ID || '';
const LAT = __ENV.LAT || '';
const LNG = __ENV.LNG || '';
const LIMIT = __ENV.LIMIT || '20';
const VUS = Number(__ENV.VUS || 10);

const recommendationTrend = new Trend('recommendations_duration', true);

export const options = {
  scenarios: {
    recommendations: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: VUS },
        { duration: '60s', target: VUS },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    'http_req_duration{expected_response:true}': ['p(95)<600'],
    'http_req_failed{expected_response:true}': ['rate<0.01'],
    checks: ['rate>0.98'],
    recommendations_duration: ['p(95)<600'],
  },
};

function headers() {
  return {
    Authorization: `Bearer ${TOKEN}`,
    Accept: 'application/json',
    'X-Correlation-Id': 'load-' + __VU + '-' + __ITER,
  };
}

function query() {
  const params = [`limit=${LIMIT}`];
  if (SITE_ID) {
    params.push(`siteId=${SITE_ID}`);
  }
  if (LAT && LNG) {
    params.push(`lat=${LAT}`, `lng=${LNG}`);
  }
  return params.join('&');
}

export function setup() {
  if (!TOKEN) {
    throw new Error('define TOKEN con un JWT de turista emitido por ms-01');
  }
  const health = http.get(`${BASE_URL}/actuator/health`);
  check(health, { 'health is up': (r) => r.status === 200 && r.body.includes('"UP"') });
  return { url: `${BASE_URL}/api/v1/recommendations?${query()}` };
}

export default function (data) {
  const response = http.get(data.url, { headers: headers(), tags: { name: 'recommendations' } });
  recommendationTrend.add(response.timings.duration);

  check(response, {
    'status is 200': (r) => r.status === 200,
    'returns items': (r) => {
      if (r.status !== 200) {
        return false;
      }
      try {
        return Array.isArray(JSON.parse(r.body).items);
      } catch (error) {
        return false;
      }
    },
    'declares a source': (r) => {
      if (r.status !== 200) {
        return false;
      }
      try {
        return typeof JSON.parse(r.body).source === 'string';
      } catch (error) {
        return false;
      }
    },
  });

  sleep(1);
}

export function handleSummary(data) {
  const failed = data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate : 1;
  const p95 = data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'] : 0;
  return {
    stdout: `\nms-05 recomendaciones: p95=${p95.toFixed(0)} ms, errores=${(failed * 100).toFixed(2)} %\n`,
    'build/k6-recommendations-summary.json': JSON.stringify(data, null, 2),
  };
}
