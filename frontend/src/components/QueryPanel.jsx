import { useState } from 'react';
import { api } from '../api/transport';

const SCENARIOS = [
  { id: 'routes-to-object', label: 'Маршруты до объекта', icon: '🚌' },
  { id: 'routes-connecting', label: 'Маршруты между объектами', icon: '🔗' },
  { id: 'can-reach', label: 'Можно ли доехать?', icon: '✅' },
  { id: 'objects-from-stop', label: 'Объекты с остановки', icon: '📍' },
  { id: 'sparql', label: 'SPARQL-запрос', icon: '💻' },
];

export function QueryPanel({ stops, routes, objects, onClose, onRouteSelect, onStopSelect }) {
  const [scenario, setScenario] = useState('routes-to-object');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Scenario params
  const [selectedObject, setSelectedObject] = useState('');
  const [selectedObject2, setSelectedObject2] = useState('');
  const [selectedRoute, setSelectedRoute] = useState('');
  const [selectedStop, setSelectedStop] = useState('');
  const [includeTransfer, setIncludeTransfer] = useState(false);
  const [sparqlQuery, setSparqlQuery] = useState(
    'PREFIX tr: <http://www.semanticweb.org/ontologies/TransportRoutes#>\n' +
    'PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n' +
    'SELECT ?route WHERE {\n  ?route rdf:type tr:Route .\n} LIMIT 10'
  );

  const reset = () => { setResult(null); setError(null); };

  const handleScenarioChange = (s) => {
    setScenario(s);
    reset();
  };

  const run = async () => {
    setLoading(true);
    setResult(null);
    setError(null);
    try {
      let data;
      switch (scenario) {
        case 'routes-to-object': {
          const direct = await api.getRoutesReaching(selectedObject);
          let viaTransfer = [];
          if (includeTransfer) {
            // Use detail endpoint for richer info
            const detail = await api.getRoutesReachingViaTransferDetail(selectedObject);
            // Group by route (route1): keep best entry per route (one transfer path is enough)
            const byRoute = new Map();
            for (const row of detail) {
              if (!byRoute.has(row.route)) byRoute.set(row.route, row);
            }
            viaTransfer = Array.from(byRoute.values()).map(row => ({
              route: row.route,
              transferStop: row.transferStop,
              transferStopName: row.transferStopName,
              route2: row.route2,
            }));
          }
          data = { type: 'routes-to-object', direct, viaTransfer };
          break;
        }
        case 'routes-connecting': {
          data = { type: 'routes-connecting', rows: await api.getRoutesConnecting(selectedObject, selectedObject2) };
          break;
        }
        case 'can-reach': {
          data = { type: 'can-reach', ...(await api.canReach(selectedRoute, selectedObject)) };
          break;
        }
        case 'objects-from-stop': {
          const direct = await api.getObjectsReachable(selectedStop);
          const viaTransfer = includeTransfer
            ? await api.getObjectsReachableViaTransfer(selectedStop)
            : [];
          data = { type: 'objects-from-stop', direct, viaTransfer };
          break;
        }
        case 'sparql': {
          data = { type: 'sparql', ...(await api.sparql(sparqlQuery)) };
          break;
        }
        default:
          data = null;
      }
      setResult(data);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="query-panel-overlay" onClick={onClose}>
      <div className="query-panel" onClick={e => e.stopPropagation()}>
        <div className="query-panel-header">
          <h2>Логические запросы к онтологии</h2>
          <button className="query-panel-close" onClick={onClose}>✕</button>
        </div>

        {/* Scenario tabs */}
        <div className="query-scenarios">
          {SCENARIOS.map(s => (
            <button
              key={s.id}
              className={`query-scenario-btn ${scenario === s.id ? 'active' : ''}`}
              onClick={() => handleScenarioChange(s.id)}
            >
              {s.icon} {s.label}
            </button>
          ))}
        </div>

        {/* Params */}
        <div className="query-params">
          {(scenario === 'routes-to-object' || scenario === 'can-reach') && (
            <div className="param-group">
              <label>Городской объект:</label>
              <select value={selectedObject} onChange={e => { setSelectedObject(e.target.value); reset(); }}>
                <option value="">— выберите объект —</option>
                {objects.map(o => (
                  <option key={o.id} value={o.id}>{o.name || o.id} ({o.type})</option>
                ))}
              </select>
            </div>
          )}

          {scenario === 'routes-connecting' && (
            <>
              <div className="param-group">
                <label>Объект A:</label>
                <select value={selectedObject} onChange={e => { setSelectedObject(e.target.value); reset(); }}>
                  <option value="">— выберите объект —</option>
                  {objects.map(o => (
                    <option key={o.id} value={o.id}>{o.name || o.id} ({o.type})</option>
                  ))}
                </select>
              </div>
              <div className="param-group">
                <label>Объект B:</label>
                <select value={selectedObject2} onChange={e => { setSelectedObject2(e.target.value); reset(); }}>
                  <option value="">— выберите объект —</option>
                  {objects.map(o => (
                    <option key={o.id} value={o.id}>{o.name || o.id} ({o.type})</option>
                  ))}
                </select>
              </div>
            </>
          )}

          {scenario === 'can-reach' && (
            <div className="param-group">
              <label>Маршрут:</label>
              <select value={selectedRoute} onChange={e => { setSelectedRoute(e.target.value); reset(); }}>
                <option value="">— выберите маршрут —</option>
                {routes.map(r => (
                  <option key={r.id} value={r.id}>{r.transport?.type} {r.transport?.number}</option>
                ))}
              </select>
            </div>
          )}

          {scenario === 'objects-from-stop' && (
            <div className="param-group">
              <label>Остановка:</label>
              <select value={selectedStop} onChange={e => { setSelectedStop(e.target.value); reset(); }}>
                <option value="">— выберите остановку —</option>
                {stops.map(s => (
                  <option key={s.id} value={s.id}>{s.name || s.id}</option>
                ))}
              </select>
            </div>
          )}

          {(scenario === 'routes-to-object' || scenario === 'objects-from-stop') && (
            <div className="param-group param-checkbox">
              <label>
                <input
                  type="checkbox"
                  checked={includeTransfer}
                  onChange={e => setIncludeTransfer(e.target.checked)}
                />
                Включить маршруты с пересадкой
              </label>
            </div>
          )}

          {scenario === 'sparql' && (
            <div className="param-group">
              <label>SPARQL-запрос:</label>
              <textarea
                className="sparql-input"
                value={sparqlQuery}
                onChange={e => { setSparqlQuery(e.target.value); reset(); }}
                rows={6}
              />
            </div>
          )}
        </div>

        <button
          className="query-run-btn"
          onClick={run}
          disabled={loading || !canRun(scenario, selectedObject, selectedObject2, selectedRoute, selectedStop, sparqlQuery)}
        >
          {loading ? 'Запрос…' : 'Выполнить запрос'}
        </button>

        {/* Result */}
        {error && <div className="query-error">{error}</div>}
        {result && <QueryResult result={result} routes={routes} onRouteSelect={onRouteSelect} />}
      </div>
    </div>
  );
}

function canRun(scenario, obj1, obj2, route, stop, sparql) {
  switch (scenario) {
    case 'routes-to-object': return !!obj1;
    case 'routes-connecting': return !!(obj1 && obj2);
    case 'can-reach': return !!(route && obj1);
    case 'objects-from-stop': return !!stop;
    case 'sparql': return sparql.trim().length > 10;
    default: return false;
  }
}

const TRANSPORT_TYPE_RU = {
  Bus: 'Автобус',
  Tram: 'Трамвай',
  Trolleybus: 'Троллейбус',
  Metro: 'Метро',
  Unknown: 'Маршрут',
};

function routeLabel(routeId, routes) {
  const found = routes.find(r => r.id === routeId);
  if (found) {
    const typeRu = TRANSPORT_TYPE_RU[found.transport?.type] || found.transport?.type || '';
    const num = found.transport?.number || '';
    return `${typeRu} ${num}`.trim();
  }
  // Fallback: parse from id like Route_Tram_40_945911
  const m = routeId.match(/^Route_(\w+?)_([^_]+)(?:_\d+)?$/);
  if (m) {
    const typeRu = TRANSPORT_TYPE_RU[m[1]] || m[1];
    return `${typeRu} ${m[2]}`;
  }
  return routeId;
}

function QueryResult({ result, routes, onRouteSelect }) {
  if (!result) return null;

  switch (result.type) {
    case 'routes-to-object':
      return (
        <div className="query-result">
          <h3>Маршруты напрямую ({result.direct.length})</h3>
          {result.direct.length === 0
            ? <p className="result-empty">Нет прямых маршрутов</p>
            : result.direct.map((r, i) => (
                <button key={i} className="result-route-btn" onClick={() => onRouteSelect?.(r.route)}>
                  {routeLabel(r.route, routes)}
                </button>
              ))
          }
          {result.viaTransfer && result.viaTransfer.length > 0 && (
            <>
              <h3>С пересадкой ({result.viaTransfer.length})</h3>
              {result.viaTransfer.map((r, i) => (
                <div key={i} className="result-transfer-row">
                  <button className="result-route-btn transfer" onClick={() => onRouteSelect?.(r.route)}>
                    {routeLabel(r.route, routes)}
                  </button>
                  {r.transferStopName && r.route2 && (
                    <span className="transfer-detail">
                      → пересадка на ост. <em>{r.transferStopName}</em> → {' '}
                      <button className="result-route-btn transfer-leg" onClick={() => onRouteSelect?.(r.route2)}>
                        {routeLabel(r.route2, routes)}
                      </button>
                    </span>
                  )}
                </div>
              ))}
            </>
          )}
        </div>
      );

    case 'routes-connecting':
      return (
        <div className="query-result">
          <h3>Маршруты, связывающие оба объекта ({result.rows.length})</h3>
          {result.rows.length === 0
            ? <p className="result-empty">Нет подходящих маршрутов</p>
            : result.rows.map((r, i) => (
                <button key={i} className="result-route-btn" onClick={() => onRouteSelect?.(r.route)}>
                  {routeLabel(r.route, routes)}
                </button>
              ))
          }
        </div>
      );

    case 'can-reach':
      return (
        <div className={`query-result can-reach-result ${result.canReach ? 'yes' : 'no'}`}>
          <h3>{result.canReach ? '✅ Да, можно доехать' : '❌ Нельзя доехать напрямую'}</h3>
          <p>Маршрут: <strong>{routeLabel(result.route, routes)}</strong></p>
          <p>Объект: <strong>{result.object}</strong></p>
        </div>
      );

    case 'objects-from-stop':
      return (
        <div className="query-result">
          <h3>Объекты напрямую ({result.direct.length})</h3>
          {result.direct.length === 0
            ? <p className="result-empty">Нет доступных объектов</p>
            : result.direct.map((r, i) => (
                <div key={i} className="result-object">
                  🏢 {r.name || r.object}
                </div>
              ))
          }
          {result.viaTransfer && result.viaTransfer.length > 0 && (
            <>
              <h3>С пересадкой ({result.viaTransfer.length})</h3>
              {result.viaTransfer.map((r, i) => (
                <div key={i} className="result-object transfer">
                  🔄 {r.name || r.object}
                </div>
              ))}
            </>
          )}
        </div>
      );

    case 'sparql':
      if (result.error) return <div className="query-error">{result.error}</div>;
      if (result.queryType === 'ASK') {
        return (
          <div className={`query-result can-reach-result ${result.booleanResult ? 'yes' : 'no'}`}>
            <h3>Результат ASK: <strong>{result.booleanResult ? 'true' : 'false'}</strong></h3>
          </div>
        );
      }
      return (
        <div className="query-result sparql-result">
          <table className="sparql-table">
            <thead>
              <tr>{(result.variables || []).map(v => <th key={v}>{v}</th>)}</tr>
            </thead>
            <tbody>
              {(result.results || []).map((row, i) => (
                <tr key={i}>
                  {(result.variables || []).map(v => <td key={v}>{row[v] || '—'}</td>)}
                </tr>
              ))}
            </tbody>
          </table>
          {(result.results || []).length === 0 && <p className="result-empty">Нет результатов</p>}
        </div>
      );

    default:
      return null;
  }
}
