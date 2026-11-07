<<<<<<< HEAD
(() => {
  'use strict';

  const service = window.CHCDataService;
  const $ = id => document.getElementById(id);
  const months = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];
  const views = {
    resumen: 'Resumen nacional',
    profesionales: 'Profesionales',
    sic: 'Comparación SIC',
    parametros: 'Parámetros',
    historial: 'Historial de cargas'
  };
  const state = { view: 'resumen', it: '', area: '', month: '', search: '' };
  let institutes = [];
  let professionalRows = [];

  const number = value => new Intl.NumberFormat('es-CL', { maximumFractionDigits: 1 })
    .format(Number(value) || 0);
  const percent = value => `${number((Number(value) || 0) * 100)}%`;
  const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[character]));

  function showError(error) {
    const box = $('api-error');
    box.textContent = `No se pudieron cargar los datos: ${error.message}`;
    box.hidden = false;
    $('api-status').textContent = 'API no disponible';
    $('database-status').textContent = 'Error de conexión';
  }

  function clearError() {
    $('api-error').textContent = '';
    $('api-error').hidden = true;
  }

  function filters(includeMonth = true) {
    const query = { it: state.it, area: state.area };
    if (includeMonth) query.mes = state.month;
    const personaId = state.search.trim();
    if (/^\d+$/.test(personaId)) query.personaId = personaId;
    return query;
  }

  function card(title, value, note, featured = false) {
    return `<div class="card ${featured ? 'featured' : ''}"><small>${escapeHtml(title)}</small><strong>${escapeHtml(value)}</strong><em>${escapeHtml(note)}</em></div>`;
  }

  function renderMonthly(rows) {
    const byMonth = new Map(rows.map(row => [Number(row.mes), row]));
    const maximum = Math.max(1, ...rows.map(row => Number(row.planTotal) || 0));
    $('chart').innerHTML = months.map((month, index) => {
      const row = byMonth.get(index + 1);
      const planned = Number(row && row.planTotal) || 0;
      const target = Number(row && row.metaGestion) || 0;
      return `<div class="chart-col" title="${month}: ${number(planned)} planificadas, ${number(target)} meta">
        <div class="chart-bars"><i style="height:${planned / maximum * 100}%"></i><i class="real" style="height:${target / maximum * 100}%"></i></div>
        <small>${month}</small></div>`;
    }).join('');
  }

  function renderDistribution(total) {
    const direct = Number(total.planDirecta) || 0;
    const indirect = Number(total.planIndirecta) || 0;
    const all = direct + indirect;
    const rows = [
      ['Atención directa planificada', direct],
      ['Atención indirecta planificada', indirect]
    ];
    $('distribution').innerHTML = rows.map(([label, amount]) => {
      const share = all ? amount * 100 / all : 0;
      return `<div class="progress-line"><div><b>${label}</b><span>${number(amount)} · ${number(share)}%</span></div>
        <div class="progress-track"><span style="width:${share}%"></span></div></div>`;
    }).join('');
  }

  function instituteName(value) {
    const match = institutes.find(item => String(item.it) === String(value)
      || String(item.instituto) === String(value));
    return match ? `${match.instituto} (IT ${match.it})` : value;
  }

  async function renderSummary() {
    const query = filters();
    const [national, grouped] = await Promise.all([
      service.national(query),
      service.summary({ ...query, agruparPor: 'instituto' })
    ]);
    const total = national.totales || {};
    $('cards').innerHTML = [
      card('Profesionales', number(total.profesionales), 'En el período filtrado'),
      card('Horas semanales', number(total.horas), 'Suma de jornadas reportadas'),
      card('Plan total', number(total.planTotal), 'Atenciones directas e indirectas'),
      card('Meta de gestión', number(total.metaGestion), 'Atenciones objetivo', true)
    ].join('');
    renderMonthly(national.porMes || []);
    renderDistribution(total);
    $('summary-head').innerHTML = `<tr><th>Instituto</th><th class="num">Profesionales</th>
      <th class="num">Horas semanales*</th><th class="num">Plan directa</th><th class="num">Plan indirecta</th>
      <th class="num">Plan total</th><th class="num">Meta gestión</th></tr>`;
    $('summary-body').innerHTML = grouped.length ? grouped.map(row => `<tr>
      <td>${escapeHtml(instituteName(row.clave))}</td><td class="num">${number(row.profesionales)}</td>
      <td class="num">${number(row.horas)}</td><td class="num">${number(row.planDirecta)}</td>
      <td class="num">${number(row.planIndirecta)}</td><td class="num">${number(row.planTotal)}</td>
      <td class="num">${number(row.metaGestion)}</td></tr>`).join('')
      : '<tr><td colspan="7" class="empty">La API no tiene resultados para estos filtros.</td></tr>';
  }

  async function renderProfessionals() {
    const query = filters();
    const rows = await service.summary({ ...query, agruparPor: 'profesional' });
    professionalRows = rows.filter(row => !state.search.trim()
      || String(row.clave).includes(state.search.trim()));
    $('people-body').innerHTML = professionalRows.length ? professionalRows.map(row => `<tr>
      <td><strong>${escapeHtml(row.clave)}</strong></td><td class="num">${number(row.horas)}</td>
      <td class="num">${number(row.planDirecta)}</td><td class="num">${number(row.planIndirecta)}</td>
      <td class="num">${number(row.planTotal)}</td><td class="num">${number(row.metaGestion)}</td>
      <td class="num">${percent(row.foja)}</td></tr>`).join('')
      : '<tr><td colspan="7" class="empty">No hay resultados para estos filtros.</td></tr>';
  }

  async function renderParameters() {
    const [cargos, generales, calendario, asignaciones] = await Promise.all([
      service.cargos(), service.generales(), service.calendario(), service.asignaciones()
    ]);
    const rows = [
      ...cargos.map(row => ['Cargo', row.cargo, `${row.unidad || ''} · estándar directo: ${row.stdDirecta} min; indirecto: ${row.stdIndirecta} min`]),
      ...generales.map(row => ['General', row.clave, `${row.valor}${row.descripcion ? ` · ${row.descripcion}` : ''}`]),
      ...(calendario.nacional || []).map(row => ['Calendario nacional', row.mes, `${row.semanasHabiles} semanas hábiles`]),
      ...(calendario.porInstituto || []).map(row => ['Calendario IT', row.it, `Mes ${row.mes}: ${row.semanasHabiles} semanas hábiles`]),
      ...asignaciones.map(row => ['Asignación', row.nombre, `${row.horasSemanales} h/sem · autorización: ${row.requiereAutorizacion ? 'requerida' : 'no requerida'}`])
    ];
    $('params-body').innerHTML = rows.map(([type, name, detail]) => `<tr>
      <td>${escapeHtml(type)}</td><td>${escapeHtml(name)}</td><td>${escapeHtml(detail)}</td></tr>`).join('')
      || '<tr><td colspan="3" class="empty">No hay parámetros en la API.</td></tr>';
  }

  async function renderHistory() {
    const [loads, changes, errors] = await Promise.all([
      service.loads(), service.changes({ limit: 100 }), service.errors(100)
    ]);
    $('loads-body').innerHTML = loads.length ? loads.map(row => `<tr>
      <td>${escapeHtml(row.fecha)}</td><td>${escapeHtml(row.archivo)}</td><td>${escapeHtml(row.resultado)}</td>
      <td>${number(row.filasLeidas)} / ${number(row.filasValidas)}</td><td>${number(row.errores)}</td></tr>`).join('')
      : '<tr><td colspan="5" class="empty">No hay cargas registradas.</td></tr>';
    $('changes-body').innerHTML = changes.length ? changes.map(row => `<tr>
      <td>${escapeHtml(row.personaId)}</td><td>${escapeHtml(row.tipo)}</td>
      <td>${escapeHtml(row.detalle)}</td><td>${escapeHtml(row.estado)}</td></tr>`).join('')
      : '<tr><td colspan="4" class="empty">No hay cambios registrados.</td></tr>';
    $('errors-body').innerHTML = errors.length ? errors.map(row => `<tr>
      <td>${escapeHtml(row.fecha)}</td><td>${escapeHtml(row.archivo)}</td><td>${number(row.fila)}</td>
      <td>${escapeHtml(row.personaId)}</td><td>${escapeHtml(row.campo)}</td><td>${escapeHtml(row.mensaje)}</td></tr>`).join('')
      : '<tr><td colspan="6" class="empty">No hay errores de carga.</td></tr>';
  }

  async function render() {
    clearError();
    if (state.view === 'resumen') await renderSummary();
    if (state.view === 'profesionales') await renderProfessionals();
    if (state.view === 'parametros') await renderParameters();
    if (state.view === 'historial') await renderHistory();
  }

  async function refresh() {
    try {
      const health = await service.health();
      $('api-status').textContent = health.estado === 'UP' ? 'API disponible' : 'API con problemas';
      $('database-status').textContent = health.estado === 'UP'
        ? `${health.modoBot} · ${number(health.contratos)} contratos` : 'Sin conexión';
      await render();
    } catch (error) {
      showError(error);
    }
  }

  function updateView(view) {
    if (!views[view]) return;
    state.view = view;
    document.querySelectorAll('.view').forEach(section => {
      section.classList.toggle('active', section.id === view);
    });
    document.querySelectorAll('[data-view]').forEach(button => {
      button.classList.toggle('active', button.dataset.view === view);
    });
    $('title').textContent = views[view];
    $('breadcrumb').textContent = views[view];
    refresh();
  }

  function exportCsv() {
    const rows = [
      ['ID persona', 'Horas semanales', 'Plan directa', 'Plan indirecta', 'Plan total', 'Meta gestión', 'FOJA'],
      ...professionalRows.map(row => [row.clave, row.horas, row.planDirecta, row.planIndirecta,
        row.planTotal, row.metaGestion, row.foja])
    ];
    const cell = value => {
      const text = String(value ?? '');
      const safe = /^[=+@\-\\t\\r]/.test(text) ? `'${text}` : text;
      return `"${safe.replace(/"/g, '""')}"`;
    };
    const blob = new Blob(['\ufeff', rows.map(row => row.map(cell).join(';')).join('\r\n')],
      { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'planificacion_api.csv';
    link.click();
    URL.revokeObjectURL(url);
  }

  async function initialize() {
    try {
      await loadFilters();
    } catch (error) {
      showError(error);
    }
    await refresh();
  }

  async function loadFilters() {
    const [availableInstitutes, areas, monthsInDb] = await Promise.all([
      service.institutes(),
      service.summary({ agruparPor: 'area' }),
      service.summary({ agruparPor: 'mes' })
    ]);
    institutes = availableInstitutes;

    const selectedInstitute = state.it;
    $('instituto').replaceChildren(new Option('Todos los institutos', ''));
    institutes.forEach(item => {
      $('instituto').add(new Option(`${item.instituto} (IT ${item.it})`, item.it));
    });
    $('instituto').value = selectedInstitute;

    const selectedArea = state.area;
    $('area').replaceChildren(new Option('Todas las áreas', ''));
    [...new Set(areas.map(row => row.clave).filter(Boolean))].sort().forEach(area => {
      $('area').add(new Option(area, area));
    });
    $('area').value = selectedArea;

    const selectedMonth = state.month;
    $('mes').replaceChildren(new Option('Todos los meses', ''));
    monthsInDb.sort((a, b) => Number(a.clave) - Number(b.clave)).forEach(row => {
      const month = Number(row.clave);
      if (month >= 1 && month <= 12) $('mes').add(new Option(months[month - 1], month));
    });
    $('mes').value = selectedMonth;
  }

  $('instituto').addEventListener('change', event => { state.it = event.target.value; refresh(); });
  $('area').addEventListener('change', event => { state.area = event.target.value; refresh(); });
  $('mes').addEventListener('change', event => { state.month = event.target.value; refresh(); });
  $('buscar').addEventListener('input', event => {
    state.search = event.target.value.replace(/\D/g, '');
    event.target.value = state.search;
    refresh();
  });
  $('limpiar').addEventListener('click', () => {
    state.it = '';
    state.area = '';
    state.month = '';
    state.search = '';
    $('instituto').value = '';
    $('area').value = '';
    $('mes').value = '';
    $('buscar').value = '';
    refresh();
  });
  $('refresh').addEventListener('click', refresh);
  $('show-people').addEventListener('click', () => updateView('profesionales'));
  $('exportar').addEventListener('click', exportCsv);
  $('upload-form').addEventListener('submit', async event => {
    event.preventDefault();
    const file = $('upload-file').files[0];
    if (!file) return;
    $('upload-status').textContent = 'Cargando y recalculando…';
    try {
      const result = await service.upload(file);
      $('upload-status').textContent = `Carga ${result.carga?.estado || result.estado || 'completada'}.`;
      await loadFilters();
      await refresh();
    } catch (error) {
      $('upload-status').textContent = `Error: ${error.message}`;
    }
  });
  document.querySelectorAll('[data-view]').forEach(button => {
    button.addEventListener('click', () => updateView(button.dataset.view));
  });

  initialize();
=======
/* Cada Hora Cuenta - vistas conectadas al backend */
(() => {
  'use strict';
  const KEY = 'botathon.session';
  const months = ['Ene','Feb','Mar','Abr','May','Jun','Jul','Ago','Sep','Oct','Nov','Dic'];
  const $ = id => document.getElementById(id);
  const service = window.CHCDataService;
  let botMode = 'desconocido';
  let user;
  try { user = JSON.parse(sessionStorage.getItem(KEY) || localStorage.getItem(KEY)); } catch {}
  if (!user || !['central','instituto'].includes(user.role) || !user.email) {
    window.location.replace('login.html'); return;
  }

  const state = { view:'resumen', loading:false };
  let institutes = [];
  const number = value => new Intl.NumberFormat('es-CL',{maximumFractionDigits:0}).format(Number(value)||0);
  const percent = value => `${(Number(value||0)*100).toFixed(1).replace('.',',')}%`;
  const escape = value => String(value??'').replace(/[&<>"']/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const status = (message, error=false) => {
    $('api-status').textContent = message;
    $('api-status').style.color = error ? '#b51c38' : '';
  };
  function filters() {
    const rawId = $('buscar').value.trim();
    return {
      it: $('instituto').value,
      area: $('area').value,
      mes: $('mes').value === 'all' ? '' : $('mes').value,
      personaId: /^\d+$/.test(rawId) ? rawId : ''
    };
  }
  function instituteName(it) {
    const found = institutes.find(item => String(item.it) === String(it));
    return found ? `${found.ciudad || found.instituto || ''} (IT ${it})` : `IT ${it}`;
  }
  function emptyRow(colspan, message) {
    return `<tr><td class="empty" colspan="${colspan}">${escape(message)}</td></tr>`;
  }
  function renderCards(totals) {
    const cards = [
      ['Profesionales',number(totals.profesionales),'En la selección actual'],
      ['Horas semanales',number(totals.horasSemanales),'Contratos en los períodos seleccionados'],
      ['Plan de atenciones',number(totals.planTotal),'Directas e indirectas'],
      ['FOJA',percent(totals.foja),'Horas directas / contratadas']
    ];
    $('cards').innerHTML = cards.map(([label,value,note],index)=>`<div class="card ${index===3?'featured':''}"><small>${label}</small><strong>${value}</strong><em>${note}</em></div>`).join('');
  }
  function renderChart(rows) {
    const byMonth = new Map(rows.map(row=>[Number(row.mes),Number(row.planTotal)||0]));
    const max = Math.max(1,...byMonth.values());
    $('chart').innerHTML = months.map((label,index)=>{
      const value=byMonth.get(index+1)||0;
      return `<div class="chart-col" title="${label}: ${number(value)} planificadas"><div class="chart-bars"><i style="height:${value?Math.max(2,value/max*100):0}%"></i></div><small>${label}</small></div>`;
    }).join('');
  }
  function renderDistribution(totals) {
    const weeklyHours=Number(totals.horasSemanales)||0;
    const directHours=Number(totals.horasDispDirectaSem)||0;
    const available=Number(totals.horasDispSem)||0;
    const items=[['Horas disponibles',available],['Atención directa',directHours],['Horas contratadas',weeklyHours]];
    $('distribution').innerHTML=items.map(([label,value])=>{
      const ratio=weeklyHours?Math.min(100,value/weeklyHours*100):0;
      return `<div class="progress-line"><div><b>${label}</b><span>${percent(ratio/100)} · ${number(value)} h</span></div><div class="progress-track"><span style="width:${ratio}%"></span></div></div>`;
    }).join('');
  }
  function renderSummary(rows) {
    const grouped = new Map();
    rows.forEach(row=>{
      const key=String(row.clave);
      const current=grouped.get(key)||{it:row.clave,profesionales:0,planTotal:0,metaGestion:0,foja:0};
      current.profesionales=Number(row.profesionales)||0;
      current.planTotal=Number(row.planTotal)||0;
      current.metaGestion=Number(row.metaGestion)||0;
      current.foja=Number(row.foja)||0;
      grouped.set(key,current);
    });
    const data=[...grouped.values()];
    $('summary-head').innerHTML='<tr><th>Instituto</th><th class="num">Profesionales</th><th class="num">Plan total</th><th class="num">Meta gestión</th><th class="num">FOJA</th></tr>';
    $('summary-body').innerHTML=data.map(row=>`<tr><td><button class="table-link" data-it="${escape(row.it)}">${escape(instituteName(row.it))} →</button></td><td class="num">${number(row.profesionales)}</td><td class="num">${number(row.planTotal)}</td><td class="num">${number(row.metaGestion)}</td><td class="num">${percent(row.foja)}</td></tr>`).join('')||emptyRow(5,'No hay planificación para estos filtros.');
    $('summary-body').querySelectorAll('[data-it]').forEach(button=>button.addEventListener('click',()=>{
      $('instituto').value=button.dataset.it; show('profesionales'); load();
    }));
  }
  function renderProfessionals(rows) {
    $('people-body').innerHTML=rows.map(row=>{
      const period=String(row.periodo||'').slice(0,7);
      return `<tr><td><strong>${escape(row.personaId)}</strong></td><td>${escape(period)}</td><td>${escape(row.ciudad||'')}</td><td>${escape(row.instituto||'')}</td><td>${escape(row.subArea||'')}</td><td>${escape(row.cargo||'')}</td><td class="num">${number(row.horasSemanales)}</td><td class="num">${number(row.planDirecta)}</td><td class="num">${number(row.planIndirecta)}</td><td class="num">${number(row.planTotal)}</td><td class="num">${number(row.metaGestion)}</td><td class="num">${percent(row.foja)}</td></tr>`;
    }).join('')||emptyRow(12,'No hay profesionales para estos filtros.');
  }
  async function load() {
    if (state.loading) return;
    state.loading=true;
    status('Consultando planificación en la base de datos…');
    try {
      const data=await service.loadDashboard(filters());
      renderCards(data.national.totales||{});
      renderChart(data.national.porMes||[]);
      renderDistribution(data.national.totales||{});
      renderSummary(data.groups);
      renderProfessionals(data.detail);
      status(`Backend conectado · Blue Prism: ${botMode} · ${number(data.detail.length)} filas (máximo 10.000).`);
    } catch(error) {
      $('summary-body').innerHTML=emptyRow(5,'No se pudo consultar la API.');
      $('people-body').innerHTML=emptyRow(12,'No se pudo consultar la API.');
      status(`Error al consultar el backend: ${error.message}`,true);
    } finally { state.loading=false; }
  }
  async function loadHistory() {
    try {
      const rows=await service.getHistory();
      $('history').innerHTML=rows.map(row=>`<tr><td>${escape(row.fecha||'')}</td><td>${escape(row.archivo||'')}</td><td>${escape(row.resultado||'')}</td><td class="num">${number(row.filasValidas)}</td><td class="num">${number(row.errores)}</td><td class="num">${number(row.cambios)}</td><td>${escape(row.detalle||'')}</td></tr>`).join('')||emptyRow(7,'Todavía no hay cargas registradas.');
    } catch(error) { $('history').innerHTML=emptyRow(7,`No se pudo consultar el historial: ${error.message}`); }
  }
  function show(view) {
    state.view=view;
    document.querySelectorAll('.view').forEach(element=>element.classList.toggle('active',element.id===view));
    document.querySelectorAll('[data-view]').forEach(element=>element.classList.toggle('active',element.dataset.view===view));
    const titles={resumen:'Resumen nacional',profesionales:'Profesionales',historial:'Historial de cargas'};
    $('title').textContent=titles[view]; $('breadcrumb').textContent=titles[view];
    if(view==='historial') loadHistory(); else load();
  }
  async function upload() {
    const file=$('excel-file').files[0];
    if(!file) { status('Selecciona el archivo Excel .xlsx que quieres cargar.',true); return; }
    if(!file.name.toLowerCase().endsWith('.xlsx')) { status('El archivo debe tener formato .xlsx.',true); return; }
    $('cargar-excel').disabled=true;
    status('Subiendo y procesando el reporte…');
    try {
      const result=await service.uploadExcel(file);
      const carga=result.carga||result;
      let outcome;
      if(String(carga.estado||result.estado||'').startsWith('ENVIADO')) {
        outcome='Archivo enviado a Blue Prism. El resultado aparecerá cuando el proceso termine.';
      } else if(String(carga.estado||'').startsWith('OK')) {
        outcome=`${carga.estado}: ${carga.mensaje||'Carga procesada.'}`;
      } else {
        outcome=`${carga.estado||result.estado||'Carga terminada'}: ${carga.mensaje||result.salidaAutomateC||''}`;
      }
      institutes=await service.getInstitutes();
      populateInstitutes();
      await load();
      if(state.view==='historial') await loadHistory();
      status(outcome,!String(carga.estado||result.estado||'').startsWith('OK')
        && !String(carga.estado||result.estado||'').startsWith('ENVIADO'));
    } catch(error) { status(`Error al cargar Excel: ${error.message}`,true); }
    finally { $('cargar-excel').disabled=false; }
  }
  function populateInstitutes() {
    const select=$('instituto'); const previous=select.value;
    select.replaceChildren(new Option('Todos los institutos',''));
    institutes.forEach(item=>select.add(new Option(`${item.ciudad||item.instituto||'Instituto'} · IT ${item.it}`,item.it)));
    if([...select.options].some(option=>option.value===previous)) select.value=previous;
  }
  async function exportCsv() {
    try {
      const blob=await service.downloadCsv(filters());
      const url=URL.createObjectURL(blob); const link=document.createElement('a');
      link.href=url; link.download='planificacion.csv'; link.click(); URL.revokeObjectURL(url);
    } catch(error) { status(`No se pudo exportar el CSV: ${error.message}`,true); }
  }

  $('username').textContent=user.name||user.email;
  $('avatar').textContent=(user.name||'U').charAt(0).toUpperCase();
  $('role-name').textContent=user.role==='central'?'Planificación central':'Jefatura de instituto';
  $('cargar-excel').hidden=user.role!=='central';
  $('logout').onclick=()=>{sessionStorage.removeItem(KEY);localStorage.removeItem(KEY);location.replace('login.html')};
  document.querySelectorAll('[data-view]').forEach(element=>element.addEventListener('click',()=>show(element.dataset.view)));
  $('show-people').onclick=()=>show('profesionales');
  $('exportar').onclick=exportCsv;
  $('cargar-excel').onclick=()=> $('excel-file').click();
  $('excel-file').onchange=()=>{if($('excel-file').files[0]) upload()};
  $('limpiar').onclick=()=>{ $('instituto').value=''; $('area').value=''; $('mes').value='all'; $('buscar').value=''; load(); };
  ['instituto','area','mes'].forEach(id=>$(id).addEventListener('change',load));
  $('buscar').addEventListener('change',load);
  $('buscar').addEventListener('keydown',event=>{if(event.key==='Enter'){event.preventDefault();load()}});
  $('refresh').onclick=()=>state.view==='historial'?loadHistory():load();
  months.forEach((name,index)=>$('mes').add(new Option(`${name} 2026`,String(index+1))));
  Promise.all([service.getInstitutes(),service.getAreas(),service.getHealth()]).then(([locations,areas,health])=>{
    institutes=locations; populateInstitutes();
    areas.forEach(area=>$('area').add(new Option(area,area)));
    botMode=health.modoBot||'desconocido';
    load();
  }).catch(error=>status(`No se pudo conectar al backend: ${error.message}`,true));
>>>>>>> 902aecbaeaf99711cf458cf8140199feb8a7a9b9
})();
