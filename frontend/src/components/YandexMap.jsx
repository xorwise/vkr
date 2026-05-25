import { useEffect, useRef, useState } from 'react';

const TRANSPORT_COLORS = {
  Bus: '#2563eb',
  Tram: '#dc2626',
  Trolleybus: '#16a34a',
  Metro: '#7c3aed',
};

const OBJECT_PRESETS = {
  Hospital: 'islands#redMedicalIcon',
  Polyclinic: 'islands#redMedicalIcon',
  School: 'islands#blueEducationIcon',
  University: 'islands#blueEducationIcon',
  Store: 'islands#grayShoppingIcon',
  ShoppingCenter: 'islands#grayShoppingIcon',
  Park: 'islands#greenLeafIcon',
  Square: 'islands#greenLeafIcon',
  Garden: 'islands#greenLeafIcon',
  Monument: 'islands#grayCircleDotIcon',
};

const SPB_CENTER = [59.9343, 30.3351];
const DEFAULT_ZOOM = 13;

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
  // New API uses latitude/longitude; old API used lat/lon
  if (entity.latitude !== undefined) {
    return entity.latitude !== 0 || entity.longitude !== 0;
  }
  return entity.lat !== 0 || entity.lon !== 0;
}

function getCoords(entity) {
  if (entity.latitude !== undefined) return [entity.latitude, entity.longitude];
  return [entity.lat, entity.lon];
}

function clearGeoObjects(map, objects) {
  objects.forEach((object) => map.geoObjects.remove(object));
  return [];
}


export function YandexMap({ stops, routes, objects, selectedRoute, selectedStop, selectedObject, onStopClick, onRouteClick }) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const ymapsRef = useRef(null);
  const stopMarkersRef = useRef([]);
  const objectMarkersRef = useRef([]);
  const routeMarkersRef = useRef([]);
  const activeRouteRef = useRef(null);
  const routeMarkerLayoutRef = useRef(null);
  const selectedRouteMarkerLayoutRef = useRef(null);
  const [mapReady, setMapReady] = useState(false);
  const [routeStatus, setRouteStatus] = useState('idle');

  const apiKey = import.meta.env.VITE_YANDEX_MAPS_KEY || '';
  const visibleStops = selectedRoute ? selectedRoute.stops || [] : [];
  const visibleObjects = selectedRoute ? [] : (objects || []);
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

      if (mapRef.current) {
        mapRef.current.destroy();
        mapRef.current = null;
      }

      ymapsRef.current = null;
      stopMarkersRef.current = [];
      objectMarkersRef.current = [];
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
      const coords = getCoords(stop);
      const marker = new ymaps.Placemark(
        coords,
        { hintContent: stop.name || stop.id },
        { preset: stop.transferPoint ? 'islands#redCircleDotIcon' : 'islands#blueCircleDotIcon' }
      );
      marker.events.add('click', () => onStopClick(stop));
      map.geoObjects.add(marker);
      stopMarkersRef.current.push(marker);
    });
  }, [mapReady, visibleStops, onStopClick]);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !ymapsRef.current) return;

    const map = mapRef.current;
    const ymaps = ymapsRef.current;

    objectMarkersRef.current = clearGeoObjects(map, objectMarkersRef.current);

    visibleObjects.filter(hasCoords).forEach((obj) => {
      const coords = getCoords(obj);
      const preset = OBJECT_PRESETS[obj.type] || 'islands#grayCircleDotIcon';
      const marker = new ymaps.Placemark(
        coords,
        { hintContent: `${obj.name || obj.id} (${obj.type})`, balloonContent: obj.name || obj.id },
        { preset }
      );
      map.geoObjects.add(marker);
      objectMarkersRef.current.push(marker);
    });
  }, [mapReady, visibleObjects]);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !ymapsRef.current) {
      return;
    }

    const map = mapRef.current;
    const ymaps = ymapsRef.current;

    routeMarkersRef.current = clearGeoObjects(map, routeMarkersRef.current);

    visibleRoutes.forEach((route) => {
      const stopsWithCoords = (route.stops || []).filter(hasCoords);
      if (stopsWithCoords.length < 2) {
        return;
      }

      const middleStop = stopsWithCoords[Math.floor(stopsWithCoords.length / 2)];
      const isSelected = selectedRoute?.id === route.id;
      const color = TRANSPORT_COLORS[route.transport?.type] || '#64748b';
      const coords = getCoords(middleStop);
      const marker = new ymaps.Placemark(
        coords,
        {
          color,
          number: route.transport?.number,
          hintContent: `Маршрут ${route.transport?.number}`,
        },
        {
          iconLayout: isSelected ? selectedRouteMarkerLayoutRef.current : routeMarkerLayoutRef.current,
          // iconOffset centres the marker on the coordinate point.
          // The marker is 34px tall; offset moves top-left corner so centre lands on point.
          iconOffset: [-17, -17],
          iconShape: {
            type: 'Circle',
            coordinates: [17, 17],
            radius: 17,
          },
        }
      );

      marker.events.add('click', () => onRouteClick?.(route));
      map.geoObjects.add(marker);
      routeMarkersRef.current.push(marker);
    });
  }, [mapReady, visibleRoutes, selectedRoute, onRouteClick]);

  // Centre map on selected stop (from sidebar click)
  useEffect(() => {
    if (!mapReady || !mapRef.current || !selectedStop) return;
    if (!hasCoords(selectedStop)) return;
    const coords = getCoords(selectedStop);
    mapRef.current.setCenter(coords, 15, { duration: 300 });
  }, [mapReady, selectedStop]);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !selectedObject) return;
    if (!hasCoords(selectedObject)) return;
    const coords = getCoords(selectedObject);
    mapRef.current.setCenter(coords, 16, { duration: 300 });
  }, [mapReady, selectedObject]);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !ymapsRef.current) {
      return undefined;
    }

    const map = mapRef.current;
    const ymaps = ymapsRef.current;

    if (activeRouteRef.current) {
      map.geoObjects.remove(activeRouteRef.current);
      activeRouteRef.current = null;
    }

    if (!selectedRoute) {
      setRouteStatus('idle');
      return undefined;
    }

    const coords = (selectedRoute.stops || []).filter(hasCoords).map(getCoords);
    if (coords.length < 2) {
      setRouteStatus('error');
      return undefined;
    }

    const color = TRANSPORT_COLORS[selectedRoute.transport?.type] || '#64748b';

    // Draw a simple polyline through stop coordinates — reliable and instant
    const polyline = new ymaps.Polyline(
      coords,
      { hintContent: `Маршрут ${selectedRoute.transport?.number}` },
      {
        strokeColor: color,
        strokeWidth: 5,
        strokeOpacity: 0.85,
      }
    );

    map.geoObjects.add(polyline);
    activeRouteRef.current = polyline;

    // Fit map to the route bounds
    map.setBounds(polyline.geometry.getBounds(), {
      checkZoomRange: true,
      zoomMargin: [40, 380, 40, 40],
    });

    setRouteStatus('ready');

    return undefined;
  }, [mapReady, selectedRoute]);

  return (
    <>
      <div ref={containerRef} className="map-container" />
      {selectedRoute && routeStatus === 'error' && (
        <div className="map-route-status error">Недостаточно остановок с координатами для отображения маршрута.</div>
      )}
    </>
  );
}
