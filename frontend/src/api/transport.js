const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8081/api';

async function get(path) {
  const res = await fetch(BASE + path);
  if (!res.ok) throw new Error(`HTTP ${res.status} ${path}`);
  return res.json();
}

export const api = {
  getStops: () => get('/stops'),
  getStop: (id) => get(`/stops/${encodeURIComponent(id)}`),
  getRoutes: (type) => get('/routes' + (type ? `?type=${type}` : '')),
  getRoute: (id) => get(`/routes/${encodeURIComponent(id)}`),
  getBuildings: () => get('/buildings'),
  getBuildingsNear: (lat, lon, radius = 500) =>
    get(`/buildings/near?lat=${lat}&lon=${lon}&radius=${radius}`),
  searchStops: (q) => get(`/search/stops?q=${encodeURIComponent(q)}`),
};
