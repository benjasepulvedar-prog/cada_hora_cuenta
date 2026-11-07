<<<<<<< HEAD
(() => {
  'use strict';

  const configuredBase = window.CHC_CONFIG && window.CHC_CONFIG.apiBaseUrl;
  const baseUrl = (configuredBase || `${window.location.origin}/api`).replace(/\/+$/, '');

  async function request(path, query = {}) {
    const url = new URL(`${baseUrl}/${path.replace(/^\/+/, '')}`);
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    });

    const response = await fetch(url, {
      headers: { Accept: 'application/json' }
    });
    if (!response.ok) {
      const body = await response.text();
      throw new Error(body || `La API respondió HTTP ${response.status}`);
    }
    return response.json();
  }

  function filtered(query) {
    return Object.fromEntries(
      Object.entries(query).filter(([, value]) => value !== undefined && value !== null && value !== '')
    );
  }

  async function upload(file) {
    const form = new FormData();
    form.append('archivo', file);
    const response = await fetch(`${baseUrl}/carga/buk`, {
      method: 'POST',
      headers: { Accept: 'application/json' },
      body: form
    });
    if (!response.ok) {
      const body = await response.text();
      throw new Error(body || `La API respondió HTTP ${response.status}`);
    }
    return response.json();
  }

  window.CHCDataService = Object.freeze({
    apiBaseUrl: baseUrl,
    health: () => request('salud'),
    institutes: () => request('institutos'),
    summary: (query = {}) => request('resumen', filtered(query)),
    national: (query = {}) => request('resumen/nacional', filtered(query)),
    professional: (personaId) => request(`resumen/profesional/${encodeURIComponent(personaId)}`),
    cargos: () => request('parametros/cargos'),
    generales: () => request('parametros/generales'),
    calendario: () => request('parametros/calendario'),
    asignaciones: () => request('parametros/asignaciones'),
    loads: () => request('carga/estado'),
    changes: (query = {}) => request('cambios', filtered(query)),
    errors: (limit = 100) => request('carga/errores', { limit }),
    upload
  });
=======
/* Cliente HTTP del backend. La interfaz nunca consulta ni modifica la BD directamente. */
(() => {
  'use strict';
  const API = window.CHC_API_BASE || 'http://localhost:8080/api';

  function query(filters = {}) {
    const params = new URLSearchParams();
    if (filters.it) params.set('it', filters.it);
    if (filters.mes) params.set('mes', filters.mes);
    if (filters.area) params.set('area', filters.area);
    if (filters.personaId) params.set('personaId', filters.personaId);
    if (filters.agruparPor) params.set('agruparPor', filters.agruparPor);
    return params.toString();
  }

  async function request(path, options) {
    const response = await fetch(`${API}${path}`, options);
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.detail || error.message || `Error HTTP ${response.status}`);
    }
    return response;
  }

  async function getJson(path) {
    const response = await request(path);
    return response.json();
  }

  function withQuery(path, filters) {
    const params = query(filters);
    return params ? `${path}?${params}` : path;
  }

  async function loadDashboard(filters) {
    const detailPath = withQuery('/resumen/detalle', filters);
    const nationalPath = withQuery('/resumen/nacional', filters);
    const groupFilters = { ...filters };
    delete groupFilters.personaId;
    const groupPath = withQuery('/resumen', { ...groupFilters, agruparPor: 'it' });
    const [national, groups, detail] = await Promise.all([
      getJson(nationalPath), getJson(groupPath), getJson(detailPath)
    ]);
    return { national, groups, detail };
  }

  async function getInstitutes() { return getJson('/institutos'); }
  async function getHealth() { return getJson('/salud'); }

  async function getAreas() {
    const rows = await getJson('/resumen?agruparPor=area');
    return rows.map(row => row.clave).filter(Boolean).sort();
  }

  async function getHistory() { return getJson('/carga/estado'); }

  async function uploadExcel(file) {
    const form = new FormData();
    form.append('archivo', file);
    const response = await request('/carga/buk', { method: 'POST', body: form });
    return response.json();
  }

  async function downloadCsv(filters) {
    const response = await request(withQuery('/resumen/exportar.csv', filters));
    return response.blob();
  }

  window.CHCDataService = Object.freeze({ loadDashboard, getInstitutes, getAreas, getHealth, getHistory, uploadExcel, downloadCsv });
>>>>>>> 902aecbaeaf99711cf458cf8140199feb8a7a9b9
})();
