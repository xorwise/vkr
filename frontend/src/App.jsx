import { useState, useCallback } from 'react';
import { YandexMap } from './components/YandexMap';
import { Sidebar } from './components/Sidebar';
import { StopPopup } from './components/StopPopup';
import { QueryPanel } from './components/QueryPanel';
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

function getStopCoords(stop) {
  if (stop.latitude !== undefined) {
    return [stop.latitude, stop.longitude];
  }
  return [stop.lat, stop.lon];
}

function areStopsDuplicate(prevStop, nextStop) {
  if (!prevStop || !nextStop) return false;

  const prevName = prevStop.name?.trim().toLowerCase();
  const nextName = nextStop.name?.trim().toLowerCase();
  if (!prevName || prevName !== nextName) return false;

  const [prevLat, prevLon] = getStopCoords(prevStop);
  const [nextLat, nextLon] = getStopCoords(nextStop);
  if ([prevLat, prevLon, nextLat, nextLon].some((value) => value === undefined)) {
    return false;
  }

  return Math.abs(prevLat - nextLat) < 0.0005 && Math.abs(prevLon - nextLon) < 0.0005;
}

function buildRouteStops(stopIds, stopById) {
  return (stopIds || []).reduce((acc, stopId) => {
    const stop = stopById[stopId] || { id: stopId, name: stopId };
    const previousStop = acc[acc.length - 1];

    if (!areStopsDuplicate(previousStop, stop)) {
      acc.push(stop);
    }

    return acc;
  }, []);
}

export default function App() {
  const { stops, routes, objects, loading, error } = useTransportData();
  const [selectedRoute, setSelectedRoute] = useState(null);
  const [selectedStop, setSelectedStop] = useState(null);
  const [selectedObject, setSelectedObject] = useState(null);
  const [showQueryPanel, setShowQueryPanel] = useState(false);
  const [showTransferOnly, setShowTransferOnly] = useState(false);
  const [isExportingOntology, setIsExportingOntology] = useState(false);

  const handleRouteSelect = useCallback((route) => {
    setSelectedStop(null);
    setSelectedObject(null);
    setSelectedRoute((prev) => (prev?.id === route.id ? null : route));
  }, []);

  const handleStopSelect = useCallback((stop) => {
    setSelectedObject(null);
    setSelectedStop(stop);
  }, []);

  const handleObjectSelect = useCallback((object) => {
    setSelectedRoute(null);
    setSelectedStop(null);
    setSelectedObject((prev) => (prev?.id === object.id ? null : object));
  }, []);

  const handleExportOntology = useCallback(async () => {
    try {
      setIsExportingOntology(true);
      const { blob, filename } = await api.exportOntology();
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (downloadError) {
      console.error(downloadError);
      window.alert('Не удалось выгрузить онтологию. Проверьте, что backend-java запущен.');
    } finally {
      setIsExportingOntology(false);
    }
  }, []);

  const handleRouteClickFromPopup = useCallback(async (routeId) => {
    try {
      const routeRaw = await api.getRoute(routeId);
      // Try to find the already-loaded route (which has full stop coords)
      const existing = routes.find(r => r.id === routeId);
      if (existing) {
        setSelectedRoute(existing);
        setSelectedStop(null);
        return;
      }
      // Fallback: enrich stop stubs with coords from the loaded stops list
      const stopById = {};
      stops.forEach(s => { stopById[s.id] = s; });
      const route = {
        ...routeRaw,
        transport: {
          type: routeRaw.transportType,
          number: routeRaw.routeRef || routeRaw.id,
        },
        stops: buildRouteStops(routeRaw.stopIds, stopById),
      };
      setSelectedRoute(route);
      setSelectedStop(null);
      setSelectedObject(null);
    } catch (e) {
      console.error(e);
    }
  }, [stops, routes]);

  const displayedStops = showTransferOnly
    ? stops.filter(s => s.transferPoint)
    : stops;

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
        stops={stops}
        objects={objects}
        onRouteSelect={handleRouteSelect}
        selectedRouteId={selectedRoute?.id}
        onStopSelect={handleStopSelect}
        onObjectSelect={handleObjectSelect}
        selectedObjectId={selectedObject?.id}
        showTransferOnly={showTransferOnly}
        onToggleTransfer={() => setShowTransferOnly(v => !v)}
        onOpenQueryPanel={() => setShowQueryPanel(true)}
        onExportOntology={handleExportOntology}
        isExportingOntology={isExportingOntology}
      />

      <main className="map-wrapper">
        <YandexMap
          stops={displayedStops}
          routes={routes}
          objects={objects}
          selectedRoute={selectedRoute}
          selectedStop={selectedStop}
          selectedObject={selectedObject}
          onStopClick={handleStopSelect}
          onRouteClick={handleRouteSelect}
        />

        {selectedRoute && (
          <div className="route-info-panel">
            <div className="route-info-header">
              <span
                className="route-info-badge"
                style={{ backgroundColor: ROUTE_COLORS[selectedRoute.transport?.type] }}
              >
                {selectedRoute.transport?.number}
              </span>
              <div className="route-info-meta">
                <h2 className="route-info-title">
                  {TRANSPORT_LABELS[selectedRoute.transport?.type]} {selectedRoute.transport?.number}
                </h2>
                <p className="route-info-stops">
                  {selectedRoute.stops?.length || 0} остановок
                </p>
              </div>
            </div>

            <div className="route-info-section">
                <h3 className="route-info-section-title">Остановки маршрута</h3>
                <ol className="route-info-stop-list">
                  {selectedRoute.stops?.map((stop) => {
                  const label = stop.name?.trim() && !stop.name.startsWith('Stop_')
                    ? stop.name.trim()
                    : `Остановка ${stop.id.replace('Stop_', '')}`;
                  return (
                    <li key={stop.id} className="route-info-stop-item">
                      {label}
                    </li>
                  );
                })}
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
        routes={routes}
        onClose={() => setSelectedStop(null)}
        onRouteClick={handleRouteClickFromPopup}
      />

      {showQueryPanel && (
        <QueryPanel
          stops={stops}
          routes={routes}
          objects={objects}
          onClose={() => setShowQueryPanel(false)}
          onRouteSelect={handleRouteClickFromPopup}
          onStopSelect={handleStopSelect}
        />
      )}
    </div>
  );
}
