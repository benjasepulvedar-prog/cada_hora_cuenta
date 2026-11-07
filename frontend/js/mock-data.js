/* DATOS DE DEMOSTRACIÓN: ficticios y anonimizados.
 * Reemplazar esta fuente con la respuesta autorizada del backend al integrar Blue Prism.
 * No reproduce todavía todas las reglas de la planilla oficial de Teletón.
 */
(() => {
  'use strict';

  const institutos = [
    'Arica', 'Iquique', 'Antofagasta', 'Calama', 'Copiapó', 'Coquimbo',
    'Valparaíso', "O’Higgins", 'Talca', 'Concepción', 'Ñuble', 'Temuco',
    'Puerto Montt', 'Coyhaique', 'Valdivia', 'Castro', 'Santiago'
  ];
  const cargos = [
    { area: 'Kinesiología', cargo: 'Kinesiólogo/a', minutes: 45 },
    { area: 'Terapia ocupacional', cargo: 'Terapeuta ocupacional', minutes: 45 },
    { area: 'Psicología', cargo: 'Psicólogo/a', minutes: 60 },
    { area: 'Fonoaudiología', cargo: 'Fonoaudiólogo/a', minutes: 40 },
    { area: 'Educación', cargo: 'Educador/a', minutes: 30, group: true },
    { area: 'Trabajo social', cargo: 'Asistente social', minutes: 60, group: true }
  ];
  const profesionales = institutos.flatMap((instituto, centro) =>
    cargos.map((especialidad, j) => {
      const number = centro * cargos.length + j + 1;
      return {
        id: `P-${String(number).padStart(4, '0')}`,
        nombre: `Profesional ${String(number).padStart(3, '0')}`,
        instituto, area: especialidad.area, cargo: especialidad.cargo,
        minutes: especialidad.minutes, group: Boolean(especialidad.group),
        horasBase: [42, 33, 22, 18, 42, 30][(j + centro) % 6],
        asignacionSemanal: number % 11 === 0 ? 2 : number % 9 === 0 ? 0.5 : 0,
        inicio: number % 27 === 0 ? 7 : 1,
        termino: number % 35 === 0 ? 9 : 12,
        licencia: number % 21 === 0 ? 8 : null
      };
    })
  );

  function diasHabiles(year, month) {
    const days = new Date(Date.UTC(year, month, 0)).getUTCDate();
    let working = 0;
    for (let d = 1; d <= days; d += 1) {
      const weekDay = new Date(Date.UTC(year, month - 1, d)).getUTCDay();
      if (weekDay !== 0 && weekDay !== 6) working += 1;
    }
    // Solo lunes a viernes. No incluye feriados ni vacaciones regionales.
    return working;
  }

  function calculos(params, ajustes = {}) {
    const rows = [];
    profesionales.forEach((p, ix) => {
      for (let month = 1; month <= 12; month += 1) {
        if (month < p.inicio || month > p.termino) continue;
        const prev = p.horasBase === 42 && month < 5 ? 44 : p.horasBase;
        const weekly = ajustes[p.id] && month >= ajustes[p.id].desde
          ? ajustes[p.id].horas : prev;
        const dias = diasHabiles(2026, month);
        const horas = p.licencia === month ? 0 : weekly * (dias / 5);
        const nas = Math.min(horas, horas * (params.nas / 100) + p.asignacionSemanal * dias / 5);
        const disponibles = Math.max(0, horas - nas);
        const suma = Math.max(1, params.directa + params.indirecta);
        const directa = disponibles * params.directa / suma;
        const indirecta = disponibles * params.indirecta / suma;
        const atencionesIndividuales = Math.floor(directa * 60 / p.minutes);
        let atencionesDirectas = atencionesIndividuales;
        if (p.group) {
          const grupos = Math.floor(atencionesIndividuales * params.grupal / 100);
          atencionesDirectas = atencionesIndividuales - grupos + grupos * params.pacientesGrupo;
        }
        const atencionesIndirectas = Math.floor(indirecta * 60 / params.minutosIndirecta);
        const planificadas = atencionesDirectas + atencionesIndirectas;
        const cumplimientoDemo = 0.72 + ((ix * 7 + month * 3) % 32) / 100;
        const realizadas = Math.floor(planificadas * cumplimientoDemo);
        rows.push({
          id: `${p.id}-${month}`, profesionalId: p.id, nombre: p.nombre,
          instituto: p.instituto, area: p.area, cargo: p.cargo,
          mes: month, horasSemanales: weekly, horas, directa, indirecta, nas,
          planificadas, realizadas, meta: Math.floor(planificadas * params.meta / 100),
          licencia: p.licencia === month,
          ajuste: Boolean(ajustes[p.id] && month >= ajustes[p.id].desde)
        });
      }
    });
    return rows;
  }

  window.CHCDemoData = Object.freeze({ institutos, cargos, profesionales, calculos });
})();
