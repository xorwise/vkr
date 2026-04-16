import { useState, useCallback } from 'react';
import { YandexMap } from './components/YandexMap';
import { Sidebar } from './components/Sidebar';
import { StopPopup } from './components/StopPopup';
import { useTransportData } from './hooks/useTransportData';
import { api } from './api/transport';
import './App.css';

const ROUTE_COLORS = {
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

export default function App() {
  const { stops, routes, buildings, loading, error } = useTransportData();
  const [selectedRoute, setSelectedRoute] = useState(null);
  const [selectedStop, setSelectedStop] = useState(null);

  const handleRouteSelect = useCallback((route) => {
    setSelectedStop(null);
    setSelectedRoute((prev) => (prev?.id === route.id ? null : route));
  }, []);

  const handleStopSelect = useCallback((stop) => {
    setSelectedStop(stop);
  }, []);

  const handleRouteClickFromPopup = useCallback(async (routeId) => {
    try {
      const route = await api.getRoute(routeId);
      setSelectedRoute(route);
      setSelectedStop(null);
    } catch (e) {
      console.error(e);
    }
  }, []);

  if (loading) {
    return (
      <div className="app-loading">
        <div className="spinner" />
        <p>Загрузка онтологии…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="app-error">
        <p>Ошибка подключения к серверу</p>
        <p className="error-detail">{error.message}</p>
      </div>
    );
  }

  return (
    <div className="app">
      <Sidebar
        routes={routes}
        onRouteSelect={handleRouteSelect}
        selectedRouteId={selectedRoute?.id}
        onStopSelect={handleStopSelect}
      />

      <main className="map-wrapper">
        <YandexMap
          stops={stops}
          routes={routes}
          buildings={buildings}
          selectedRoute={selectedRoute}
          onStopClick={handleStopSelect}
          onRouteClick={handleRouteSelect}
        />

        {selectedRoute && (
          <div className="route-info-panel">
            <div className="route-info-header">
              <span
                className="route-info-badge"
                style={{ backgroundColor: ROUTE_COLORS[selectedRoute.transport.type] }}
              >
                {selectedRoute.transport.number}
              </span>
              <div className="route-info-meta">
                <h2 className="route-info-title">
                  {TRANSPORT_LABELS[selectedRoute.transport.type]} {selectedRoute.transport.number}
                </h2>
                <p className="route-info-stops">
                  {selectedRoute.stops?.length || 0} остановок
                </p>
              </div>
            </div>

            <p className="route-info-description">
              Маршрут проходит через остановки {selectedRoute.stops?.map((stop) => stop.name).join(', ')}.
            </p>

            <div className="route-info-section">
              <h3 className="route-info-section-title">Остановки маршрута</h3>
              <ol className="route-info-stop-list">
                {selectedRoute.stops?.map((stop) => (
                  <li key={stop.id} className="route-info-stop-item">
                    {stop.name}
                  </li>
                ))}
              </ol>
            </div>

            <button
              className="route-info-close"
              onClick={() => setSelectedRoute(null)}
            >
              ✕
            </button>
          </div>
        )}
      </main>

      <StopPopup
        stop={selectedStop}
        onClose={() => setSelectedStop(null)}
        onRouteClick={handleRouteClickFromPopup}
      />
    </div>
  );
}
