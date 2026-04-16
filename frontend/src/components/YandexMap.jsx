import { useEffect, useRef, useState } from 'react';

const TRANSPORT_COLORS = {
  Bus: '#2563eb',
  Tram: '#dc2626',
  Trolleybus: '#16a34a',
  Metro: '#7c3aed',
};

const BUILDING_ICONS = {
  School: 'Школа',
  Hospital: 'Больница',
  Polyclinic: 'Поликлиника',
  Store: 'Магазин',
  University: 'Университет',
};

const SPB_CENTER = [59.9343, 30.3351];
const DEFAULT_ZOOM = 13;
const MAX_ROUTE_POINTS = 10;

let ymapsPromise;

function loadYmaps(apiKey) {
  if (!apiKey) {
    return Promise.reject(new Error('Yandex Maps API key is missing'));
  }

  if (window.ymaps) {
    return new Promise((resolve) => window.ymaps.ready(() => resolve(window.ymaps)));
  }

  if (ymapsPromise) {
    return ymapsPromise;
  }

  ymapsPromise = new Promise((resolve, reject) => {
    const existingScript = document.querySelector('script[data-yandex-maps="v2"]');

    const handleLoad = () => {
      if (!window.ymaps) {
        reject(new Error('Yandex Maps API did not initialize'));
        return;
      }

      window.ymaps.ready(() => resolve(window.ymaps));
    };

    if (existingScript) {
      existingScript.addEventListener('load', handleLoad, { once: true });
      existingScript.addEventListener('error', reject, { once: true });
      return;
    }

    const script = document.createElement('script');
    script.src = `https://api-maps.yandex.ru/2.1/?apikey=${apiKey}&lang=ru_RU`;
    script.async = true;
    script.defer = true;
    script.dataset.yandexMaps = 'v2';
    script.onload = handleLoad;
    script.onerror = reject;
    document.head.appendChild(script);
  });

  return ymapsPromise;
}

function hasCoords(entity) {
  return entity.lat !== 0 || entity.lon !== 0;
}

function clearGeoObjects(map, objects) {
  objects.forEach((object) => map.geoObjects.remove(object));
  return [];
}

function getRouteReferencePoints(stops) {
  const stopsWithCoords = stops.filter(hasCoords);
  if (stopsWithCoords.length <= 2) {
    return stopsWithCoords.map((stop) => [stop.lat, stop.lon]);
  }

  // Yandex routing becomes unstable with long stop chains, so sample anchor stops.
  const indexes = new Set([0, stopsWithCoords.length - 1]);
  const step = (stopsWithCoords.length - 1) / (MAX_ROUTE_POINTS - 1);

  for (let i = 1; i < MAX_ROUTE_POINTS - 1; i += 1) {
    indexes.add(Math.round(i * step));
  }

  return [...indexes]
    .sort((left, right) => left - right)
    .map((index) => stopsWithCoords[index])
    .filter((stop, index, array) => index === 0 || stop.id !== array[index - 1].id)
    .map((stop) => [stop.lat, stop.lon]);
}

export function YandexMap({ stops, routes, buildings, selectedRoute, onStopClick, onRouteClick }) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const ymapsRef = useRef(null);
  const stopMarkersRef = useRef([]);
  const buildingMarkersRef = useRef([]);
  const routeMarkersRef = useRef([]);
  const activeRouteRef = useRef(null);
  const routeRequestIdRef = useRef(0);
  const routeMarkerLayoutRef = useRef(null);
  const selectedRouteMarkerLayoutRef = useRef(null);
  const [mapReady, setMapReady] = useState(false);
  const [routeStatus, setRouteStatus] = useState('idle');

  const apiKey = import.meta.env.VITE_YANDEX_MAPS_KEY || '';
  const visibleStops = selectedRoute ? selectedRoute.stops || [] : stops;
  const visibleBuildings = selectedRoute ? [] : buildings;
  const visibleRoutes = selectedRoute ? [selectedRoute] : routes;

  useEffect(() => {
    if (!containerRef.current || mapRef.current) {
      return undefined;
    }

    let cancelled = false;

    loadYmaps(apiKey)
      .then((ymaps) => {
        if (cancelled || !containerRef.current || mapRef.current) {
          return;
        }

        ymapsRef.current = ymaps;
        routeMarkerLayoutRef.current = ymaps.templateLayoutFactory.createClass(
          '<div class="route-map-marker" style="background-color: {{ properties.color }};">{{ properties.number }}</div>'
        );
        selectedRouteMarkerLayoutRef.current = ymaps.templateLayoutFactory.createClass(
          '<div class="route-map-marker selected" style="background-color: {{ properties.color }};">{{ properties.number }}</div>'
        );

        mapRef.current = new ymaps.Map(
          containerRef.current,
          {
            center: SPB_CENTER,
            zoom: DEFAULT_ZOOM,
            controls: ['zoomControl'],
          },
          {
            suppressMapOpenBlock: true,
          }
        );
        setMapReady(true);
      })
      .catch((error) => console.error('Yandex Maps load error:', error));

    return () => {
      cancelled = true;
      routeRequestIdRef.current += 1;

      if (mapRef.current) {
        mapRef.current.destroy();
        mapRef.current = null;
      }

      ymapsRef.current = null;
      stopMarkersRef.current = [];
      buildingMarkersRef.current = [];
      routeMarkersRef.current = [];
      activeRouteRef.current = null;
      setMapReady(false);
      setRouteStatus('idle');
    };
  }, [apiKey]);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !ymapsRef.current) {
      return;
    }

    const map = mapRef.current;
    const ymaps = ymapsRef.current;

    stopMarkersRef.current = clearGeoObjects(map, stopMarkersRef.current);

    visibleStops.filter(hasCoords).forEach((stop) => {
      const marker = new ymaps.Placemark(
        [stop.lat, stop.lon],
        {
          hintContent: stop.name,
        },
        {
          preset: 'islands#blueCircleDotIcon',
        }
      );

      marker.events.add('click', () => onStopClick(stop));
      map.geoObjects.add(marker);
      stopMarkersRef.current.push(marker);
    });
  }, [mapReady, visibleStops, onStopClick]);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !ymapsRef.current) {
      return;
    }

    const map = mapRef.current;
    const ymaps = ymapsRef.current;

    buildingMarkersRef.current = clearGeoObjects(map, buildingMarkersRef.current);

    visibleBuildings.filter(hasCoords).forEach((building) => {
      const marker = new ymaps.Placemark(
        [building.lat, building.lon],
        {
          hintContent: `${building.name} (${BUILDING_ICONS[building.type] || building.type})`,
        },
        {
          preset: 'islands#grayCircleDotIcon',
        }
      );

      map.geoObjects.add(marker);
      buildingMarkersRef.current.push(marker);
    });
  }, [mapReady, visibleBuildings]);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !ymapsRef.current) {
      return;
    }

    const map = mapRef.current;
    const ymaps = ymapsRef.current;

    routeMarkersRef.current = clearGeoObjects(map, routeMarkersRef.current);

    visibleRoutes.forEach((route) => {
      const stopsWithCoords = route.stops.filter(hasCoords);
      if (stopsWithCoords.length < 2) {
        return;
      }

      const middleStop = stopsWithCoords[Math.floor(stopsWithCoords.length / 2)];
      const isSelected = selectedRoute?.id === route.id;
      const color = TRANSPORT_COLORS[route.transport.type] || '#64748b';
      const marker = new ymaps.Placemark(
        [middleStop.lat, middleStop.lon],
        {
          color,
          number: route.transport.number,
          hintContent: `Маршрут ${route.transport.number}`,
        },
        {
          iconLayout: isSelected ? selectedRouteMarkerLayoutRef.current : routeMarkerLayoutRef.current,
          iconOffset: [-17, -17],
          iconShape: {
            type: 'Circle',
            coordinates: [0, 0],
            radius: 20,
          },
        }
      );

      marker.events.add('click', () => onRouteClick?.(route));
      map.geoObjects.add(marker);
      routeMarkersRef.current.push(marker);
    });
  }, [mapReady, visibleRoutes, selectedRoute, onRouteClick]);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !ymapsRef.current) {
      return undefined;
    }

    const map = mapRef.current;
    const ymaps = ymapsRef.current;
    const requestId = routeRequestIdRef.current + 1;
    routeRequestIdRef.current = requestId;

    if (activeRouteRef.current) {
      map.geoObjects.remove(activeRouteRef.current);
      activeRouteRef.current = null;
    }

    if (!selectedRoute) {
      setRouteStatus('idle');
      return undefined;
    }

    const referencePoints = getRouteReferencePoints(selectedRoute.stops || []);
    if (referencePoints.length < 2) {
      setRouteStatus('error');
      return undefined;
    }

    let cancelled = false;
    const color = TRANSPORT_COLORS[selectedRoute.transport.type] || '#64748b';

    setRouteStatus('loading');

    ymaps.route(referencePoints, {
      multiRoute: true,
      routingMode: 'masstransit',
    }).then(
      (multiRoute) => {
        if (cancelled || requestId !== routeRequestIdRef.current || !mapRef.current) {
          return;
        }

        multiRoute.options.set({
          boundsAutoApply: true,
          zoomMargin: [40, 380, 40, 40],
          wayPointVisible: false,
          viaPointVisible: false,
          routeStrokeColor: color,
          routeStrokeOpacity: 0.3,
          routeStrokeWidth: 4,
          routeActiveStrokeColor: color,
          routeActiveStrokeOpacity: 0.95,
          routeActiveStrokeWidth: 6,
        });

        mapRef.current.geoObjects.add(multiRoute);
        activeRouteRef.current = multiRoute;
        setRouteStatus('ready');
      },
      (error) => {
        if (cancelled || requestId !== routeRequestIdRef.current) {
          return;
        }

        console.error('Yandex route build error:', error);
        setRouteStatus('error');
      }
    );

    return () => {
      cancelled = true;
    };
  }, [mapReady, selectedRoute]);

  return (
    <>
      <div ref={containerRef} className="map-container" />
      {selectedRoute && routeStatus === 'loading' && (
        <div className="map-route-status">Yandex строит маршрут по выбранным остановкам…</div>
      )}
      {selectedRoute && routeStatus === 'error' && (
        <div className="map-route-status error">Yandex не смог построить маршрут для этой последовательности остановок.</div>
      )}
    </>
  );
}
