/* Cada Hora Cuenta - dashboard y mapa interactivo */
(() => {
      'use strict';
      const KEY = 'botathon.session';
      let user;
      try { user = JSON.parse(sessionStorage.getItem(KEY) || localStorage.getItem(KEY)); } catch {}
      if (!user || !['central', 'instituto'].includes(user.role) || !user.email) {
        window.location.replace('login.html'); return;
      }
      if (!window.CHCDataService || !window.CHCDemoData) {
        alert('No se encontraron js/data-service.js o js/mock-data.js'); return;
      }
      const $ = id => document.getElementById(id);
      const service = window.CHCDataService;
      const central = user.role === 'central';
      const names = ['Ene','Feb','Mar','Abr','May','Jun','Jul','Ago','Sep','Oct','Nov','Dic'];
      const places = [
        ['Arica',-18.4783,-70.3126], ['Iquique',-20.2141,-70.1524],
        ['Antofagasta',-23.6509,-70.3975], ['Calama',-22.4544,-68.9294],
        ['Copiapó',-27.3668,-70.3323], ['Coquimbo',-29.9533,-71.3436],
        ['Valparaíso',-33.0472,-71.6127], ['Santiago',-33.4489,-70.6693],
        ['O’Higgins',-34.1708,-70.7444], ['Talca',-35.4264,-71.6554],
        ['Ñuble',-36.6063,-72.1034], ['Concepción',-36.8270,-73.0498],
        ['Temuco',-38.7359,-72.5904], ['Valdivia',-39.8174,-73.2459],
        ['Puerto Montt',-41.4717,-72.9361], ['Castro',-42.4801,-73.7624],
        ['Coyhaique',-45.5752,-72.0662]
      ];
      const labels = {directa:'Atención directa (%)',indirecta:'Atención indirecta (%)',nas:'No asistencial / NAS (%)',minutosIndirecta:'Minutos atención indirecta',grupal:'Atención grupal (%)',pacientesGrupo:'Pacientes por grupo',meta:'Meta de gestión (%)'};
      const state = {view:'resumen',instituto:central?'':'Santiago',area:'',mes:'all',buscar:''};
      let map, markers=[];
      const number = x => new Intl.NumberFormat('es-CL',{maximumFractionDigits:0}).format(x||0);
      const percent = x => `${Number(x||0).toFixed(1).replace('.',',')}%`;
      const sum = (rows,key) => rows.reduce((t,r)=>t+(+r[key]||0),0);
      const ratio = (a,b) => b ? a*100/b : 0;
      const escape = s => String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
      function stats(rows){
        const plan=sum(rows,'planificadas'),horas=sum(rows,'horas');
        return {prof:new Set(rows.map(r=>r.profesionalId)).size,horas,plan,real:sum(rows,'realizadas'),directa:sum(rows,'directa'),indirecta:sum(rows,'indirecta'),nas:sum(rows,'nas'),meta:Math.floor(plan*service.getParams().meta/100),cumplimiento:ratio(sum(rows,'realizadas'),plan)};
      }
      function grouped(rows,key){const result=new Map();rows.forEach(r=>{if(!result.has(r[key]))result.set(r[key],[]);result.get(r[key]).push(r)});return result}
      function filtered(){
        const search=state.buscar.trim().toLowerCase();
        return service.getVisibleRows(user).filter(r=>(!state.instituto||r.instituto===state.instituto)&&(!state.area||r.area===state.area)&&(state.mes==='all'||r.mes===+state.mes)&&(!search||(r.nombre+' '+r.profesionalId).toLowerCase().includes(search)));
      }
      function people(rows){return [...grouped(rows,'profesionalId')].map(([id,items])=>({id,nombre:items[0].nombre,instituto:items[0].instituto,area:items[0].area,...stats(items)}))}
      function statCard(title,value,note,featured=false){return `<div class="card ${featured?'featured':''}"><small>${escape(title)}</small><strong>${escape(value)}</strong><em>${escape(note)}</em></div>`}
      function setInstitute(name){
        if(!central && name!=='Santiago')return;
        state.instituto=name;$('instituto').value=name;render();
      }
      function initMap(){
        if(!window.L){$('map').innerHTML='<div class="map-fallback">No se pudo cargar el mapa. Verifica internet. La lista de lugares sigue funcionando.</div>';return;}
        map=L.map('map',{scrollWheelZoom:false,zoomControl:true}).setView([-35.5,-71],5);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'© OpenStreetMap contributors',maxZoom:17}).addTo(map);
        const pin=L.divIcon({className:'',html:'<span style="display:block;width:15px;height:15px;background:#e32a49;border:3px solid white;border-radius:50%;box-shadow:0 2px 9px #7f153c88"></span>',iconSize:[15,15],iconAnchor:[7,7]});
        places.forEach(([name,lat,lng])=>{
          const marker=L.marker([lat,lng],{icon:pin}).addTo(map);
          marker.bindTooltip(name,{direction:'top'});
          marker.on('click',()=>setInstitute(name));markers.push({name,marker});
        });
        setTimeout(()=>map.invalidateSize(),120);
      }
      function renderMap(rows){
        const m=stats(rows);
        $('hero-title').textContent=state.instituto?`Planificación de ${state.instituto}`:'Una red que conecta todo Chile';
        $('hero-description').textContent=central?'Selecciona una sede en el mapa para ver sus profesionales y metas de atención.':'Consulta los datos de Santiago; los demás lugares se muestran como referencia.';
        $('hero-stats').innerHTML=`<div><strong>${number(new Set(rows.map(r=>r.instituto)).size)}</strong><small>institutos con datos</small></div><div><strong>${number(m.prof)}</strong><small>profesionales</small></div><div><strong>${number(m.plan)}</strong><small>atenciones</small></div>`;
        const totals=grouped(rows,'instituto');
        $('map-list').innerHTML=places.map(([name])=>{
          const x=totals.has(name)?stats(totals.get(name)):null;
          const disabled=!central&&name!=='Santiago';
          return `<button class="place ${state.instituto===name?'active':''}" data-place="${escape(name)}" ${disabled?'disabled':''} type="button"><strong>📍 ${escape(name)}</strong><small>${x?`${number(x.prof)} profesionales · ${number(x.plan)} atenciones`:'Sin datos con este filtro'}</small></button>`;
        }).join('');
        $('map-list').querySelectorAll('[data-place]').forEach(b=>b.addEventListener('click',()=>setInstitute(b.dataset.place)));
        if(map){markers.forEach(({name,marker})=>marker.setOpacity(!central&&name!=='Santiago'?.35:1));map.invalidateSize()}
      }
      function renderChart(rows){
        const byMonth=names.map((_,i)=>rows.filter(r=>r.mes===i+1));
        const max=Math.max(1,...byMonth.map(r=>stats(r).plan));
        $('chart').innerHTML=byMonth.map((r,i)=>{
          const m=stats(r);return `<div class="chart-col" title="${names[i]}: ${number(m.plan)} planificadas, ${number(m.real)} realizadas"><div class="chart-bars"><i style="height:${Math.max(0,m.plan/max*100)}%"></i><i class="real" style="height:${Math.max(0,m.real/max*100)}%"></i></div><small>${names[i]}</small></div>`;
        }).join('');
      }
      function renderResumen(rows){
        const m=stats(rows);
        $('cards').innerHTML=[statCard('Profesionales',number(m.prof),'Activos en el período'),statCard('Horas contratadas',number(m.horas),'Total del período'),statCard('Atenciones planificadas',number(m.plan),'Directas e indirectas'),statCard('Meta de gestión',number(m.meta),`${service.getParams().meta}% del total`,true)].join('');
        renderMap(rows);renderChart(rows);
        $('distribution').innerHTML=[['Atención directa','directa'],['Atención indirecta','indirecta'],['No asistencial · NAS','nas']].map(([label,key])=>`<div class="progress-line"><div><b>${label}</b><span>${percent(ratio(m[key],m.horas))} · ${number(m[key])} h</span></div><div class="progress-track"><span style="width:${ratio(m[key],m.horas)}%"></span></div></div>`).join('');
        const aggregate=central&&!state.instituto&&!state.buscar;
        $('table-title').textContent=aggregate?'Planificación por instituto':'Planificación por profesional';
        const table=aggregate?[...grouped(rows,'instituto')].map(([name,items])=>({name,...stats(items)})):people(rows).map(p=>({...p,name:p.nombre}));
        $('summary-head').innerHTML=`<tr><th>${aggregate?'Instituto':'Profesional'}</th><th class="num">Profesionales</th><th class="num">Horas</th><th class="num">Planificadas</th><th class="num">Realizadas</th><th class="num">Meta</th><th class="num">Cumplimiento</th></tr>`;
        $('summary-body').innerHTML=table.map(r=>`<tr><td>${aggregate?`<button class="table-link" data-table-place="${escape(r.name)}">${escape(r.name)} →</button>`:`<strong>${escape(r.name)}</strong>`}</td><td class="num">${number(r.prof)}</td><td class="num">${number(r.horas)}</td><td class="num">${number(r.plan)}</td><td class="num">${number(r.real)}</td><td class="num">${number(r.meta)}</td><td class="num">${percent(r.cumplimiento)}</td></tr>`).join('')||'<tr><td colspan="7" class="empty">No hay datos para estos filtros.</td></tr>';
        $('summary-body').querySelectorAll('[data-table-place]').forEach(b=>b.addEventListener('click',()=>{setInstitute(b.dataset.tablePlace);show('profesionales')}));
      }
      function renderProfesionales(rows){
        $('people-body').innerHTML=people(rows).map(p=>`<tr><td><strong>${escape(p.nombre)}</strong><br><small>${escape(p.id)}</small></td><td>${escape(p.instituto)}</td><td>${escape(p.area)}</td><td class="num">${number(p.horas)}</td><td class="num">${number(p.plan)}</td><td class="num">${number(p.real)}</td><td class="num">${percent(p.cumplimiento)}</td></tr>`).join('')||'<tr><td colspan="7" class="empty">No hay profesionales.</td></tr>';
      }
      function renderSic(rows){
        const m=stats(rows);
        $('sic-cards').innerHTML=[statCard('Planificadas',number(m.plan),'Período seleccionado'),statCard('Realizadas',number(m.real),'SIC simulado'),statCard('Cumplimiento',percent(m.cumplimiento),'Realizadas / planificadas',true)].join('');
        $('sic-body').innerHTML=people(rows).map(p=>`<tr><td><strong>${escape(p.nombre)}</strong></td><td>${escape(p.instituto)}</td><td>${escape(p.area)}</td><td class="num">${number(p.meta)}</td><td class="num">${number(p.real)}</td><td class="num"><span class="badge ${p.cumplimiento>=90?'ok':''}">${percent(p.cumplimiento)}</span></td></tr>`).join('')||'<tr><td colspan="6" class="empty">Sin registros.</td></tr>';
      }
      function renderParams(){
        const p=service.getParams();$('params-fields').innerHTML=Object.keys(labels).map(key=>`<label>${escape(labels[key])}<input name="${key}" type="number" step="any" min="0" value="${p[key]}" required></label>`).join('');
      }
      function renderHistory(){
        const h=service.getAudit();$('history').innerHTML=h.length?h.map(e=>`<div class="history-item"><strong>${escape(e.action)}</strong><small>${escape(e.actor)} · ${new Date(e.date).toLocaleString('es-CL')}</small><p>${escape(e.detail)}</p></div>`).join(''):'<p>Sin modificaciones registradas todavía.</p>';
      }
      function render(){const rows=filtered();if(state.view==='resumen')renderResumen(rows);if(state.view==='profesionales')renderProfesionales(rows);if(state.view==='sic')renderSic(rows);if(state.view==='parametros')renderParams();if(state.view==='historial')renderHistory()}
      function show(view){if(!central&&['parametros','historial'].includes(view))return;state.view=view;document.querySelectorAll('.view').forEach(el=>el.classList.toggle('active',el.id===view));document.querySelectorAll('[data-view]').forEach(el=>el.classList.toggle('active',el.dataset.view===view));const titles={resumen:central?'Resumen nacional':'Resumen de Santiago',profesionales:'Profesionales',sic:'Comparación SIC',parametros:'Parámetros',historial:'Historial'};$('title').textContent=titles[view];$('breadcrumb').textContent=titles[view];render();if(view==='resumen'&&map)setTimeout(()=>map.invalidateSize(),80)}
      function exportCsv(){
        const data=[['Código','Profesional','Instituto','Área','Horas','Planificadas','Realizadas'],...people(filtered()).map(p=>[p.id,p.nombre,p.instituto,p.area,p.horas,p.plan,p.real])];
        const cell=v=>{let s=String(v);if(/^[=+@\-\t\r]/.test(s))s="'"+s;return '"'+s.replace(/"/g,'""')+'"'};
        const file=new Blob(['\ufeff'+data.map(r=>r.map(cell).join(';')).join('\r\n')],{type:'text/csv;charset=utf-8'});const url=URL.createObjectURL(file);const a=document.createElement('a');a.href=url;a.download='planificacion_demo.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
      }
      // Inicialización de sesión y filtros.
      $('username').textContent=user.name||user.email;
      $('avatar').textContent=(user.name||'U').charAt(0).toUpperCase();
      $('role-name').textContent=central?'Planificación central':'Jefatura · Santiago';
      document.querySelectorAll('[data-central]').forEach(el=>el.hidden=!central);
      window.CHCDemoData.institutos.filter(n=>central||n==='Santiago').forEach(n=>$('instituto').add(new Option(n,n)));
      $('instituto').value=state.instituto;$('instituto').disabled=!central;
      [...new Set(window.CHCDemoData.cargos.map(c=>c.area))].sort().forEach(n=>$('area').add(new Option(n,n)));
      names.forEach((n,i)=>$('mes').add(new Option(`${n} 2026`,String(i+1))));
      $('instituto').onchange=e=>{state.instituto=e.target.value;render()};
      $('area').onchange=e=>{state.area=e.target.value;render()};
      $('mes').onchange=e=>{state.mes=e.target.value;render()};
      $('buscar').oninput=e=>{state.buscar=e.target.value;render()};
      $('limpiar').onclick=()=>{state.instituto=central?'':'Santiago';state.area='';state.mes='all';state.buscar='';$('instituto').value=state.instituto;$('area').value='';$('mes').value='all';$('buscar').value='';render()};
      document.querySelectorAll('[data-view]').forEach(el=>el.onclick=()=>show(el.dataset.view));
      $('logout').onclick=()=>{sessionStorage.removeItem(KEY);localStorage.removeItem(KEY);location.replace('login.html')};
      $('show-people').onclick=()=>show('profesionales');$('exportar').onclick=exportCsv;
      $('simulate').onclick=()=>{const e=service.simulateBuk(user);if(e){state.instituto=e.instituto;$('instituto').value=e.instituto;show('resumen');alert(e.description)}};
      $('params-form').onsubmit=e=>{e.preventDefault();if(!central)return;const p={};for(const key of Object.keys(labels))p[key]=Number(e.target.elements[key].value);const sumPercent=p.directa+p.indirecta+p.nas;if(Object.values(p).some(x=>!Number.isFinite(x)||x<0)||Math.abs(sumPercent-100)>0.001||p.minutosIndirecta<=0||p.pacientesGrupo<1||p.directa+p.indirecta<=0||p.meta<1||p.meta>100||p.grupal>100){$('params-error').textContent='Revisa los valores: directa + indirecta + NAS deben sumar 100%.';return}if(service.saveParams(user,p)){$('params-error').textContent='';show('resumen')}};
      $('restaurar').onclick=()=>{if(central&&confirm('¿Restaurar los datos de demostración?')){service.reset(user);show('resumen')}};
      initMap();show('resumen');
    })();
