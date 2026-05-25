const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

async function get(path) {
  const res = await fetch(BASE + path);
  if (!res.ok) throw new Error(`HTTP ${res.status} ${path}`);
  return res.json();
}

async function post(path, body) {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} ${path}`);
  return res.json();
}

async function download(path) {
  const res = await fetch(BASE + path);
  if (!res.ok) throw new Error(`HTTP ${res.status} ${path}`);

  const blob = await res.blob();
  const disposition = res.headers.get('content-disposition') || '';
  const filenameMatch = disposition.match(/filename=([^;]+)/i);
  const filename = filenameMatch ? filenameMatch[1].replace(/"/g, '').trim() : 'ontology.owl';

  return { blob, filename };
}

export const api = {
  // === Остановки ===
  getStops: () => get('/stops'),
  getStop: (id) => get(`/stops/${encodeURIComponent(id)}`),
  getTransferStops: () => get('/stops/transfer'),

  // === Маршруты ===
  getRoutes: (type) => get('/routes' + (type ? `?type=${type}` : '')),
  getRoute: (id) => get(`/routes/${encodeURIComponent(id)}`),

  // === Городские объекты ===
  getObjects: (category) => get('/objects' + (category ? `?category=${category}` : '')),
  getObject: (id) => get(`/objects/${encodeURIComponent(id)}`),

  // === Логические запросы ===
  getRoutesReaching: (objectId) => get(`/query/routes-reaching?object=${encodeURIComponent(objectId)}`),
  getRoutesReachingViaTransfer: (objectId) => get(`/query/routes-reaching-via-transfer?object=${encodeURIComponent(objectId)}`),
  getRoutesReachingViaTransferDetail: (objectId) => get(`/query/routes-reaching-via-transfer-detail?object=${encodeURIComponent(objectId)}`),
  getRoutesConnecting: (fromId, toId) => get(`/query/routes-connecting?from=${encodeURIComponent(fromId)}&to=${encodeURIComponent(toId)}`),
  getObjectsReachable: (stopId) => get(`/query/objects-reachable?stop=${encodeURIComponent(stopId)}`),
  getObjectsReachableViaTransfer: (stopId) => get(`/query/objects-reachable-via-transfer?stop=${encodeURIComponent(stopId)}`),
  canReach: (routeId, objectId) => get(`/query/can-reach?route=${encodeURIComponent(routeId)}&object=${encodeURIComponent(objectId)}`),
  getMedicalFacilities: () => get('/query/medical-facilities'),
  getEducationalFacilities: () => get('/query/educational-facilities'),

  // === Онтология ===
  getOntologyClasses: () => get('/ontology/classes'),
  getOntologyProperties: () => get('/ontology/properties'),
  getOntologyIndividuals: () => get('/ontology/individuals'),
  exportOntology: () => download('/ontology/export'),
  sparql: (query) => post('/sparql', { query }),

  // === Импорт ===
  importOsm: (bbox) => {
    const url = '/import/osm' + (bbox ? `?bbox=${encodeURIComponent(bbox)}` : '');
    return post(url, {});
  },
  getImportStatus: () => get('/import/status'),
  addCityObject: (data) => post('/import/object', data),
};
