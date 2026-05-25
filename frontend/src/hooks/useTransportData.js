import { useState, useEffect } from 'react';
import { api } from '../api/transport';

let transportDataPromise = null;
let transportDataCache = null;

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
    const stop = stopById[stopId] || { id: stopId, name: '' };
    const previousStop = acc[acc.length - 1];

    // OSM often returns adjacent platform/stop_position pairs for one stop.
    if (!areStopsDuplicate(previousStop, stop)) {
      acc.push(stop);
    }

    return acc;
  }, []);
}

export function clearTransportCache() {
  transportDataPromise = null;
  transportDataCache = null;
}

function loadTransportData() {
  if (transportDataCache) {
    return Promise.resolve(transportDataCache);
  }

  if (!transportDataPromise) {
    transportDataPromise = Promise.all([
      api.getStops(),
      api.getRoutes(),
      api.getObjects(),
    ]).then(([stops, routes, objects]) => {
      // Build a lookup map so route stops get full coordinates
      const stopById = {};
      stops.forEach(s => { stopById[s.id] = s; });

      // Normalize routes: new API returns { id, routeRef, transportType, stopIds }
      // Old frontend expected { id, transport: { type, number }, stops: [...] }
      const normalizedRoutes = routes
        .filter(r => r.transportType && r.transportType !== 'Unknown')
        .map(r => ({
          ...r,
          transport: {
            type: r.transportType,
            number: r.routeRef || r.id,
          },
          // Use full stop objects and collapse adjacent stop_position/platform duplicates.
          stops: buildRouteStops(r.stopIds, stopById),
        }));

      transportDataCache = { stops, routes: normalizedRoutes, objects };
      return transportDataCache;
    }).catch((error) => {
      transportDataPromise = null;
      throw error;
    });
  }

  return transportDataPromise;
}

export function useTransportData() {
  const [stops, setStops] = useState([]);
  const [routes, setRoutes] = useState([]);
  const [objects, setObjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    loadTransportData()
      .then((data) => {
        if (cancelled) return;
        setStops(data.stops);
        setRoutes(data.routes);
        setObjects(data.objects);
      })
      .catch((nextError) => {
        if (!cancelled) setError(nextError);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, []);

  return { stops, routes, objects, loading, error };
}
