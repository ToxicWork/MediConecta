// Prueba de carga de la API REST (eje C de los criterios de la cátedra): 50 usuarios concurrentes durante 5 minutos
// sobre los tres endpoints críticos del paciente, con p95 < 500 ms y menos de 1 % de errores en cada uno.
//
//   1. GET /profesionales/{id}/disponibilidad   buscar horarios libres (lo primero que hace un paciente)
//   2. GET /turnos                              mis turnos
//   3. GET /turnos/{id}                         el detalle de un turno (con su ETag)
//
// Cada usuario virtual repite los tres pedidos con 1 s de pausa entre uno y otro, como alguien que navega. Necesita
// los datos de pruebas/carga/preparar_datos.py (horarios publicados y turnos confirmados del paciente de demo).
// Deja el resumen en resultados-carga/ (resumen.json y reporte.md). Termina con error si no se cumple un umbral.
//
// Uso:  k6 run pruebas/carga/carga_api.js
//       Variables opcionales: MEDICONECTA_API (https://localhost:8443/mediconecta/api/v1), USUARIOS (50), DURACION (5m)
import http from 'k6/http';
import { check, sleep } from 'k6';

const API = __ENV.MEDICONECTA_API || 'https://localhost:8443/mediconecta/api/v1';
const PACIENTE = 'juan.perez@mediconecta.test';
const USUARIOS = Number(__ENV.USUARIOS || 50);
const DURACION = __ENV.DURACION || '5m';
const ENDPOINTS = {
  disponibilidad: 'GET /profesionales/{id}/disponibilidad',
  mis_turnos: 'GET /turnos',
  detalle_turno: 'GET /turnos/{id}',
};

export const options = {
  insecureSkipTLSVerify: true, // el certificado autofirmado que WildFly genera para localhost
  scenarios: {
    pacientes: { executor: 'constant-vus', vus: USUARIOS, duration: DURACION, gracefulStop: '15s' },
  },
  thresholds: Object.assign(
    { 'http_req_failed{tipo:carga}': ['rate<0.01'], 'checks{tipo:carga}': ['rate>0.99'] },
    ...Object.keys(ENDPOINTS).map((e) => ({
      [`http_req_duration{endpoint:${e}}`]: ['p(95)<500'],
      [`http_req_failed{endpoint:${e}}`]: ['rate<0.01'],
    })),
  ),
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function preparacion(url, params) {
  const r = http.get(url, Object.assign({ tags: { tipo: 'preparacion' } }, params));
  if (r.status !== 200) throw new Error(`${url}: ${r.status}`);
  return r.json();
}

export function setup() {
  const login = http.post(`${API}/auth/login`, JSON.stringify({ email: PACIENTE, password: __ENV.CLAVE_DEMO || 'demo1234' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { tipo: 'preparacion' } });
  if (login.status !== 200) throw new Error(`login del paciente de demo: ${login.status}`);
  const token = login.json('token');
  const profesional = preparacion(`${API}/profesionales`).find((p) => p.nombre.includes('Ana Lopez'));
  const auth = { headers: { Authorization: `Bearer ${token}` } };
  const turno = preparacion(`${API}/turnos`, auth).find((t) => t.estado === 'CONFIRMADO');
  if (!profesional || !turno) throw new Error('faltan datos: correr antes pruebas/carga/preparar_datos.py');
  const libres = preparacion(`${API}/profesionales/${profesional.id}/disponibilidad`).length;
  console.log(`Profesional ${profesional.id} con ${libres} horarios libres; turno ${turno.id} del paciente de demo.`);
  return { token, profesionalId: profesional.id, turnoId: turno.id };
}

export default function (datos) {
  const auth = { Authorization: `Bearer ${datos.token}` };
  const disponibilidad = http.get(`${API}/profesionales/${datos.profesionalId}/disponibilidad`,
    { tags: { endpoint: 'disponibilidad', tipo: 'carga' } });
  check(disponibilidad, { 'disponibilidad: 200 con horarios': (r) => r.status === 200 && r.json().length > 0 },
    { tipo: 'carga' });
  sleep(1);
  const misTurnos = http.get(`${API}/turnos`, { headers: auth, tags: { endpoint: 'mis_turnos', tipo: 'carga' } });
  check(misTurnos, { 'mis turnos: 200 con el turno confirmado': (r) => r.status === 200
    && r.json().some((t) => t.id === datos.turnoId) }, { tipo: 'carga' });
  sleep(1);
  const detalle = http.get(`${API}/turnos/${datos.turnoId}`, { headers: auth, tags: { endpoint: 'detalle_turno', tipo: 'carga' } });
  check(detalle, { 'detalle: 200 con su ETag': (r) => r.status === 200 && !!r.headers.Etag }, { tipo: 'carga' });
  sleep(1);
}

function ms(valor) {
  return valor === undefined ? '-' : `${valor.toFixed(1)} ms`;
}

function umbrales(metrica) {
  const t = metrica && metrica.thresholds ? Object.values(metrica.thresholds) : [];
  return t.length && t.every((u) => u.ok) ? 'cumple' : 'NO CUMPLE';
}

export function handleSummary(data) {
  const m = data.metrics;
  const filas = Object.entries(ENDPOINTS).map(([clave, nombre]) => {
    const d = m[`http_req_duration{endpoint:${clave}}`];
    const f = m[`http_req_failed{endpoint:${clave}}`];
    const v = d ? d.values : {};
    const pedidos = f ? f.values.passes + f.values.fails : 0;
    const errores = f ? (100 * f.values.rate).toFixed(2) : '-';
    const estado = umbrales(d) === 'cumple' && umbrales(f) === 'cumple' ? 'cumple' : 'NO CUMPLE';
    return `| ${nombre} | ${pedidos} | ${ms(v.med)} | ${ms(v['p(95)'])} | ${ms(v['p(99)'])} | ${ms(v.max)} | ${errores} % | ${estado} |`;
  });
  const total = m['http_req_failed{tipo:carga}'];
  const pedidos = total ? total.values.passes + total.values.fails : 0;
  const reporte = [
    '# Prueba de carga de la API REST de MediConecta',
    '',
    `${USUARIOS} usuarios virtuales durante ${DURACION}, cada uno con los tres pedidos y 1 s de pausa entre pedidos.`,
    'Umbrales: p95 < 500 ms y errores < 1 % en cada endpoint.',
    '',
    '| Endpoint | Pedidos | Mediana | p95 | p99 | Máximo | Errores | Umbrales |',
    '| --- | --- | --- | --- | --- | --- | --- | --- |',
    ...filas,
    '',
    `Total: ${pedidos} pedidos, ${total ? (100 * total.values.rate).toFixed(2) : '-'} % de errores; `
      + `verificaciones de contenido: ${m['checks{tipo:carga}'] ? (100 * m['checks{tipo:carga}'].values.rate).toFixed(2) : '-'} % OK.`,
    '',
  ].join('\n');
  return {
    'resultados-carga/resumen.json': JSON.stringify(data, null, 2),
    'resultados-carga/reporte.md': reporte,
    stdout: `${reporte}\n`,
  };
}
