// Пиковый сценарий (T-14.5): имитация рекламной рассылки — 500 пользователей за 30с
// на главную (листинг) и категорию.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 500 },
        { duration: '2m', target: 500 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: { http_req_failed: ['rate<0.02'] },
};

export default function () {
  const res = http.batch([
    ['GET', `${BASE}/api/v1/products?page=0&size=20`],
    ['GET', `${BASE}/api/v1/categories`],
  ]);
  res.forEach((r) => check(r, { ok: (x) => x.status === 200 }));
}
