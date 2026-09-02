// Основной read-heavy трафик (T-14.5): листинг каталога 75%, карточка 20%, поиск 5%.
// Рост до 200 одновременных пользователей, плато 10 минут, спад.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errors = new Rate('errors');
const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    read_heavy: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 200 }, // рост
        { duration: '10m', target: 200 }, // плато
        { duration: '1m', target: 0 },   // спад
      ],
    },
  },
  thresholds: {
    'http_req_duration{name:list}': ['p(95)<300'],   // листинг p95 < 300 мс
    'http_req_duration{name:card}': ['p(95)<200'],   // карточка p95 < 200 мс
    errors: ['rate<0.005'],                           // доля ошибок < 0.5%
  },
};

export default function () {
  const r = Math.random();
  if (r < 0.75) {
    const res = http.get(`${BASE}/api/v1/products?paint_type=INTERIOR&page=0&size=20`, { tags: { name: 'list' } });
    check(res, { 'list 200': (x) => x.status === 200 }) || errors.add(1);
  } else if (r < 0.95) {
    const res = http.get(`${BASE}/api/v1/products/1`, { tags: { name: 'card' } });
    check(res, { 'card ok': (x) => x.status === 200 || x.status === 404 }) || errors.add(1);
  } else {
    const res = http.get(`${BASE}/api/v1/products?q=paint`, { tags: { name: 'search' } });
    check(res, { 'search 200': (x) => x.status === 200 }) || errors.add(1);
  }
  sleep(Math.random() * 2);
}
