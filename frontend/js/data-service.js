/* Puerta de entrada de datos, separada de la interfaz.
 * Hoy: datos ficticios en navegador. Futuro: backend conectado con Blue Prism.
 * IMPORTANTE: en producción el backend debe validar la sesión, rol e instituto
 * y devolver solo los registros que el usuario pueda consultar.
 */
(() => {
  'use strict';
  const DEFAULTS = Object.freeze({
    directa: 60, indirecta: 25, nas: 15,
    minutosIndirecta: 30, grupal: 15, pacientesGrupo: 3, meta: 90
  });
  const PARAM_KEY = 'botathon.demo.params.v1';
  const ADJUST_KEY = 'botathon.demo.adjust.v1';
  const AUDIT_KEY = 'botathon.demo.audit.v1';

  function read(key, fallback) {
    try { return JSON.parse(localStorage.getItem(key)) ?? fallback; }
    catch { return fallback; }
  }
  function write(key, value) { localStorage.setItem(key, JSON.stringify(value)); }
  function getParams() { return { ...DEFAULTS, ...read(PARAM_KEY, {}) }; }
  function setParams(next) { write(PARAM_KEY, next); }
  function getAudit() { return read(AUDIT_KEY, []); }
  function pushAudit(event) {
    const history = getAudit();
    history.unshift({ ...event, date: new Date().toISOString() });
    write(AUDIT_KEY, history.slice(0, 40));
  }
  function getVisibleRows(session) {
    const rows = window.CHCDemoData.calculos(getParams(), read(ADJUST_KEY, {}));
    // Filtro funcional de DEMO, no seguridad. En producción lo hace el servidor.
    if (session.role === 'central') return rows;
    if (session.role === 'instituto') return rows.filter(r => r.instituto === 'Santiago');
    return [];
  }
  function simulateBuk(session) {
    if (!session || session.role !== 'central') return null;
    const overrides = read(ADJUST_KEY, {});
    const p = window.CHCDemoData.profesionales.find(item => item.instituto === 'Santiago' && item.horasBase === 42);
    if (!p) return null;
    const hadChange = Boolean(overrides[p.id]);
    if (hadChange) delete overrides[p.id];
    else overrides[p.id] = { desde: 7, horas: 30 };
    write(ADJUST_KEY, overrides);
    const description = hadChange
      ? `${p.nombre}: se revirtió la jornada simulada, desde julio 2026.`
      : `${p.nombre}: jornada de 42 a 30 h semanales desde julio 2026.`;
    pushAudit({ action: 'Simulación BUK', detail: description, actor: session.name || 'Planificación central' });
    return { description, applied: !hadChange, instituto: p.instituto };
  }
  function saveParams(session, params) {
    if (!session || session.role !== 'central') return false;
    setParams(params);
    pushAudit({ action: 'Cambio de parámetros (demo)', detail: 'Se modificaron las reglas globales de demostración.', actor: session.name || 'Planificación central' });
    return true;
  }
  function reset(session) {
    if (!session || session.role !== 'central') return;
    [PARAM_KEY, ADJUST_KEY, AUDIT_KEY].forEach(key => localStorage.removeItem(key));
  }

  // Reemplazo posterior sugerido:
  // async function getVisibleRows(session) {
  //   const response = await fetch('/api/planificacion?anio=2026', { credentials: 'include' });
  //   if (!response.ok) throw new Error('No se pudieron obtener los datos');
  //   return response.json(); // API filtra por permisos, no confiar en session.role del navegador
  // }
  window.CHCDataService = Object.freeze({ DEFAULTS, getParams, getAudit, getVisibleRows, simulateBuk, saveParams, reset });
})();
