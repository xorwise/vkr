import { useState } from 'react';
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

export function Sidebar({ routes, onRouteSelect, selectedRouteId, onStopSelect }) {
  const [typeFilter, setTypeFilter] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState(null);
  const [searching, setSearching] = useState(false);

  const types = ['', 'Bus', 'Tram', 'Trolleybus', 'Metro'];

  const filteredRoutes = routes.filter(
    (r) => !typeFilter || r.transport.type === typeFilter
  );

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    setSearching(true);
    try {
      const results = await api.searchStops(searchQuery.trim());
      setSearchResults(results);
    } catch {
      setSearchResults([]);
    } finally {
      setSearching(false);
    }
  };

  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <h1 className="sidebar-title">Транспорт СПб</h1>
        <p className="sidebar-subtitle">Онтология городских маршрутов</p>
      </div>

      {/* Search */}
      <div className="sidebar-section">
        <form onSubmit={handleSearch} className="search-form">
          <input
            type="text"
            placeholder="Поиск остановки…"
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              if (!e.target.value) setSearchResults(null);
            }}
            className="search-input"
          />
          <button type="submit" className="search-btn" disabled={searching}>
            {searching ? '…' : '🔍'}
          </button>
        </form>

        {searchResults !== null && (
          <div className="search-results">
            {searchResults.length === 0 ? (
              <p className="search-empty">Ничего не найдено</p>
            ) : (
              searchResults.map((s) => (
                <button
                  key={s.id}
                  className="search-result-item"
                  onClick={() => {
                    onStopSelect(s);
                    setSearchResults(null);
                    setSearchQuery('');
                  }}
                >
                  <span className="stop-icon">🚏</span>
                  {s.name}
                </button>
              ))
            )}
          </div>
        )}
      </div>

      {/* Type filter */}
      <div className="sidebar-section">
        <div className="filter-tabs">
          {types.map((t) => (
            <button
              key={t}
              className={`filter-tab ${typeFilter === t ? 'active' : ''}`}
              style={
                t && typeFilter === t
                  ? { backgroundColor: TRANSPORT_COLORS[t], color: '#fff', borderColor: TRANSPORT_COLORS[t] }
                  : {}
              }
              onClick={() => setTypeFilter(t)}
            >
              {t ? TRANSPORT_LABELS[t] : 'Все'}
            </button>
          ))}
        </div>
      </div>

      {/* Route list */}
      <div className="sidebar-section routes-list">
        <h2 className="section-title">
          Маршруты{' '}
          <span className="route-count">{filteredRoutes.length}</span>
        </h2>
        {filteredRoutes.map((route) => (
          <button
            key={route.id}
            className={`route-item ${selectedRouteId === route.id ? 'selected' : ''}`}
            onClick={() => onRouteSelect(route)}
          >
            <span
              className="route-badge"
              style={{ backgroundColor: TRANSPORT_COLORS[route.transport.type] }}
            >
              {route.transport.number}
            </span>
            <span className="route-type">{TRANSPORT_LABELS[route.transport.type]}</span>
            <span className="route-stops">{route.stops?.length || 0} ост.</span>
          </button>
        ))}
      </div>
    </aside>
  );
}
