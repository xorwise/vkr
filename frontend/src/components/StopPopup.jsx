import { useState, useEffect } from 'react';
import { api } from '../api/transport';

const TRANSPORT_COLORS = {
  Bus: '#2563eb',
  Tram: '#dc2626',
  Trolleybus: '#16a34a',
  Metro: '#7c3aed',
};

const TRANSPORT_LABELS = {
  Bus: 'Авт.',
  Tram: 'Трам.',
  Trolleybus: 'Тролл.',
  Metro: 'Метро',
};

const BUILDING_ICONS = {
  School: '🏫',
  Hospital: '🏥',
  Polyclinic: '⚕️',
  Store: '🛒',
  University: '🎓',
};

export function StopPopup({ stop, onClose, onRouteClick }) {
  const [details, setDetails] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!stop) return;
    setLoading(true);
    api.getStop(stop.id)
      .then(setDetails)
      .catch(() => setDetails(null))
      .finally(() => setLoading(false));
  }, [stop?.id]);

  if (!stop) return null;

  return (
    <div className="popup-overlay" onClick={onClose}>
      <div className="popup" onClick={(e) => e.stopPropagation()}>
        <button className="popup-close" onClick={onClose}>✕</button>

        <div className="popup-header">
          <span className="popup-icon">🚏</span>
          <h2 className="popup-title">{stop.name}</h2>
        </div>

        {stop.lat !== 0 && (
          <p className="popup-coords">
            {stop.lat.toFixed(5)}, {stop.lon.toFixed(5)}
          </p>
        )}

        {loading ? (
          <p className="popup-loading">Загрузка…</p>
        ) : details ? (
          <>
            {details.routes?.length > 0 && (
              <div className="popup-section">
                <h3 className="popup-section-title">Маршруты через остановку</h3>
                <div className="popup-routes">
                  {details.routes.map((r) => (
                    <button
                      key={r.id}
                      className="popup-route-tag"
                      style={{ backgroundColor: TRANSPORT_COLORS[r.transport_type] }}
                      onClick={() => onRouteClick && onRouteClick(r.id)}
                    >
                      {TRANSPORT_LABELS[r.transport_type]} {r.number}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {details.buildings?.length > 0 && (
              <div className="popup-section">
                <h3 className="popup-section-title">Объекты рядом</h3>
                <ul className="popup-buildings">
                  {details.buildings.map((b) => (
                    <li key={b.id} className="popup-building-item">
                      <span>{BUILDING_ICONS[b.type] || '🏢'}</span>
                      <span>{b.name}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </>
        ) : (
          <p className="popup-error">Не удалось загрузить данные</p>
        )}
      </div>
    </div>
  );
}
