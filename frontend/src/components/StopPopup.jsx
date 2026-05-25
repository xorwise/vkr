import { useState, useEffect } from 'react';
import { api } from '../api/transport';

const TRANSPORT_COLORS = {
  Bus: '#2563eb',
  Tram: '#dc2626',
  Trolleybus: '#16a34a',
  Metro: '#7c3aed',
};

const TRANSPORT_LABELS = {
  Bus: 'Автобус',
  Tram: 'Трамвай',
  Trolleybus: 'Троллейбус',
  Metro: 'Метро',
};

const OBJECT_ICONS = {
  School: '🏫',
  Hospital: '🏥',
  Polyclinic: '⚕️',
  Store: '🛒',
  University: '🎓',
  ShoppingCenter: '🏬',
  Park: '🌳',
  Square: '🌿',
  Garden: '🌺',
  Monument: '🏛️',
};

export function StopPopup({ stop, routes, onClose, onRouteClick }) {
  const [reachableObjects, setReachableObjects] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!stop) return;
    setLoading(true);
    setReachableObjects([]);
    api.getObjectsReachable(stop.id)
      .then(setReachableObjects)
      .catch(() => setReachableObjects([]))
      .finally(() => setLoading(false));
  }, [stop?.id]);

  if (!stop) return null;

  // Маршруты через эту остановку — ищем в уже загруженных данных
  const routesThrough = (routes || []).filter(r =>
    (r.stopIds || r.stops?.map(s => s.id) || []).includes(stop.id)
  );

  return (
    <div className="popup-overlay" onClick={onClose}>
      <div className="popup" onClick={(e) => e.stopPropagation()}>
        <button className="popup-close" onClick={onClose}>✕</button>

        <div className="popup-header">
          <span className="popup-icon">{stop.transferPoint ? '🔴' : '🚏'}</span>
          <div>
            <h2 className="popup-title">{stop.name || stop.id}</h2>
            {stop.transferPoint && (
              <span className="popup-transfer-badge">Пересадочная остановка</span>
            )}
          </div>
        </div>

        {stop.latitude && (
          <p className="popup-coords">
            {stop.latitude.toFixed(5)}, {stop.longitude?.toFixed(5)}
          </p>
        )}

        {/* Маршруты через остановку */}
        {routesThrough.length > 0 && (
          <div className="popup-section">
            <h3 className="popup-section-title">Маршруты через эту остановку</h3>
            <div className="popup-routes">
              {routesThrough.map((r) => (
                <button
                  key={r.id}
                  className="popup-route-btn"
                  style={{ backgroundColor: TRANSPORT_COLORS[r.transport?.type] || '#64748b' }}
                  onClick={() => { onRouteClick?.(r.id); onClose(); }}
                  title={`${TRANSPORT_LABELS[r.transport?.type] || r.transport?.type} №${r.transport?.number}`}
                >
                  {r.transport?.number}
                </button>
              ))}
            </div>
            <p className="popup-routes-legend">
              {routesThrough.map(r =>
                `${TRANSPORT_LABELS[r.transport?.type] || r.transport?.type} №${r.transport?.number}`
              ).join(', ')}
            </p>
          </div>
        )}

        {/* Доступные объекты */}
        {loading ? (
          <p className="popup-loading">Загрузка доступных объектов…</p>
        ) : reachableObjects.length > 0 ? (
          <div className="popup-section">
            <h3 className="popup-section-title">Объекты поблизости</h3>
            <ul className="popup-buildings">
              {reachableObjects.map((obj, i) => {
                const typeKey = obj.object ? obj.object.replace(/_.*/,'') : '';
                return (
                  <li key={i} className="popup-building-item">
                    <span>{OBJECT_ICONS[typeKey] || '🏢'}</span>
                    <span>{obj.name || obj.object}</span>
                  </li>
                );
              })}
            </ul>
          </div>
        ) : null}
      </div>
    </div>
  );
}
