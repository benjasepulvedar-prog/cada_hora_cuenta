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
})();
