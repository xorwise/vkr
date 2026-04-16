import { useState, useEffect } from 'react';
import { api } from '../api/transport';

let transportDataPromise = null;
let transportDataCache = null;

function loadTransportData() {
  if (transportDataCache) {
    return Promise.resolve(transportDataCache);
  }

  if (!transportDataPromise) {
    transportDataPromise = Promise.all([
      api.getStops(),
      api.getRoutes(),
      api.getBuildings(),
    ]).then(([stops, routes, buildings]) => {
      transportDataCache = { stops, routes, buildings };
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
  const [buildings, setBuildings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    loadTransportData()
      .then((data) => {
        if (cancelled) {
          return;
        }

        setStops(data.stops);
        setRoutes(data.routes);
        setBuildings(data.buildings);
      })
      .catch((nextError) => {
        if (!cancelled) {
          setError(nextError);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return { stops, routes, buildings, loading, error };
}
