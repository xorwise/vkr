import { useState } from 'react';

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

export function Sidebar({
  routes,
  stops,
  objects,
  onRouteSelect,
  selectedRouteId,
  onStopSelect,
   onObjectSelect,
  selectedObjectId,
  showTransferOnly,
  onToggleTransfer,
  onOpenQueryPanel,
  onExportOntology,
  isExportingOntology,
}) {
  const [typeFilter, setTypeFilter] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState(null);
  const [activeTab, setActiveTab] = useState('routes'); // 'routes' | 'stops' | 'objects'

  const types = ['', 'Bus', 'Tram', 'Trolleybus', 'Metro'];

  const filteredRoutes = routes.filter(
    (r) => !typeFilter || r.transport?.type === typeFilter
  );

  const handleSearch = (e) => {
    e.preventDefault();
    const q = searchQuery.trim().toLowerCase();
    if (!q) return;
    const results = stops.filter(s => s.name?.toLowerCase().includes(q));
    setSearchResults(results);
  };

  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <h1 className="sidebar-title">Транспорт СПб</h1>
        <p className="sidebar-subtitle">Онтология городских маршрутов</p>
      </div>

      {/* Actions */}
      <div className="sidebar-section sidebar-actions">
        <button
          className={`action-btn ${showTransferOnly ? 'active' : ''}`}
          onClick={onToggleTransfer}
          title="Показать только пересадочные остановки"
        >
          {showTransferOnly ? '🔴' : '🔵'} Пересадочные
        </button>
        <button
          className="action-btn"
          onClick={onOpenQueryPanel}
          title="Логические запросы к онтологии"
        >
          🔎 Запросы
        </button>
        <button
          className="action-btn"
          onClick={onExportOntology}
          title="Скачать текущую онтологию в формате OWL"
          disabled={isExportingOntology}
        >
          {isExportingOntology ? '⏳ Выгрузка...' : '⬇️ Выгрузить OWL'}
        </button>
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
          <button type="submit" className="search-btn">
            🔍
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
                  <span className="stop-icon">{s.transferPoint ? '🔴' : '🚏'}</span>
                  {s.name}
                </button>
              ))
            )}
          </div>
        )}
      </div>

      {/* Tabs */}
      <div className="sidebar-tabs">
        {[['routes', 'Маршруты'], ['stops', 'Остановки'], ['objects', 'Объекты']].map(([tab, label]) => (
          <button
            key={tab}
            className={`sidebar-tab ${activeTab === tab ? 'active' : ''}`}
            onClick={() => setActiveTab(tab)}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Routes tab */}
      {activeTab === 'routes' && (
        <div className="sidebar-section routes-list">
          {/* Type filter */}
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
          <h2 className="section-title">
            Маршруты <span className="route-count">{filteredRoutes.length}</span>
          </h2>
          {filteredRoutes.map((route) => (
            <button
              key={route.id}
              className={`route-item ${selectedRouteId === route.id ? 'selected' : ''}`}
              onClick={() => onRouteSelect(route)}
            >
              <span
                className="route-badge"
                style={{ backgroundColor: TRANSPORT_COLORS[route.transport?.type] }}
              >
                {route.transport?.number}
              </span>
              <span className="route-type">{TRANSPORT_LABELS[route.transport?.type]}</span>
              <span className="route-stops">{route.stops?.length || 0} ост.</span>
            </button>
          ))}
        </div>
      )}

      {/* Stops tab */}
      {activeTab === 'stops' && (
        <div className="sidebar-section stops-list">
          <h2 className="section-title">
            Остановки <span className="route-count">{stops.length}</span>
          </h2>
          {stops.map((stop) => (
            <button
              key={stop.id}
              className="stop-item"
              onClick={() => onStopSelect(stop)}
            >
              <span>{stop.transferPoint ? '🔴' : '🚏'}</span>
              <span className="stop-name">{stop.name || stop.id}</span>
              {stop.transferPoint && <span className="transfer-badge">пересадка</span>}
            </button>
          ))}
        </div>
      )}

      {/* Objects tab */}
      {activeTab === 'objects' && (
        <div className="sidebar-section objects-list">
          <h2 className="section-title">
            Объекты <span className="route-count">{objects?.length || 0}</span>
          </h2>
          {(objects || []).map((obj) => (
            <button
              key={obj.id}
              className={`object-item ${selectedObjectId === obj.id ? 'selected' : ''}`}
              onClick={() => onObjectSelect?.(obj)}
            >
              <span>{getObjectIcon(obj.type)}</span>
              <div className="object-info">
                <span className="object-name">{obj.name || obj.id}</span>
                <span className="object-type">{obj.type}</span>
              </div>
            </button>
          ))}
        </div>
      )}
    </aside>
  );
}

function getObjectIcon(type) {
  const icons = {
    Hospital: '🏥',
    Polyclinic: '⚕️',
    School: '🏫',
    University: '🎓',
    Store: '🛒',
    ShoppingCenter: '🏬',
    Park: '🌳',
    Square: '🌿',
    Garden: '🌺',
    Monument: '🏛️',
  };
  return icons[type] || '🏢';
}
