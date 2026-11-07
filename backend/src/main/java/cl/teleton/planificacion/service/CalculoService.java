package cl.teleton.planificacion.service;

import cl.teleton.planificacion.calculo.Formula;
import cl.teleton.planificacion.config.DatabaseDialect;
import cl.teleton.planificacion.model.ContratoRow;
import cl.teleton.planificacion.repository.ContratoRepository;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aplica las reglas parametrizadas y persiste el resultado por profesional y mes. */
@Service
public class CalculoService {

    private final JdbcTemplate jdbc;
    private final ContratoRepository contratos;
    private final DatabaseDialect dialect;

    public CalculoService(JdbcTemplate jdbc, ContratoRepository contratos, DatabaseDialect dialect) {
        this.jdbc = jdbc;
        this.contratos = contratos;
        this.dialect = dialect;
    }

    private record Ctx(Map<String, Formula.Regla> reglas, Map<String, double[]> grupal, double[] semanasNac,
                       Map<String, Double> semanasIt, double metaPct, int minInd, Map<Integer, Double> asignaciones) {}
    private record Pendiente(long id, int personaId, int it, String tipo, LocalDate desde) {}

    @Transactional
    public Map<String, Object> recalcularTodo() {
        Ctx ctx = cargarContexto();
        jdbc.update("DELETE FROM resultado_planificacion");
        Map<String, List<ContratoRow>> porPersona = contratos.vigentes().stream()
                .collect(Collectors.groupingBy(c -> c.personaId() + "|" + c.it(), LinkedHashMap::new, Collectors.toList()));
        int filas = 0, sinRegla = 0;
        for (List<ContratoRow> grupo : porPersona.values()) {
            int[] resultado = calcularGrupo(ctx, grupo, null);
            filas += resultado[0];
            sinRegla += resultado[1];
        }
        return Map.of("profesionales", porPersona.size(), "filasCalculadas", filas, "filasSinRegla", sinRegla);
    }

    @Transactional
    public int recalcularPersona(int personaId, Integer it, LocalDate desde) {
        Ctx ctx = cargarContexto();
        Map<String, List<ContratoRow>> porIt = contratos.vigentesDePersona(personaId, it).stream()
                .collect(Collectors.groupingBy(c -> c.personaId() + "|" + c.it(), LinkedHashMap::new, Collectors.toList()));
        int filas = 0;
        for (List<ContratoRow> grupo : porIt.values()) filas += calcularGrupo(ctx, grupo, desde)[0];
        return filas;
    }

    @Transactional
    public Map<String, Object> aplicarPendientes() {
        List<Pendiente> pendientes = jdbc.query(
                "SELECT id, persona_id, it, tipo, periodo_desde FROM cambios_detectados WHERE estado = 'PENDIENTE' ORDER BY id",
                (rs, i) -> new Pendiente(rs.getLong("id"), rs.getInt("persona_id"), rs.getInt("it"),
                        rs.getString("tipo"), rs.getDate("periodo_desde").toLocalDate()));
        if (pendientes.isEmpty()) return Map.of("aplicados", 0);

        Ctx ctx = cargarContexto();
        int filas = 0;
        for (Pendiente pendiente : pendientes) {
            if (pendiente.tipo().equals("BAJA")) {
                jdbc.update("DELETE FROM resultado_planificacion WHERE persona_id = ? AND it = ? AND periodo >= ?",
                        pendiente.personaId(), pendiente.it(), Date.valueOf(pendiente.desde()));
            } else {
                List<ContratoRow> grupo = contratos.vigentesDePersona(pendiente.personaId(), pendiente.it());
                filas += calcularGrupo(ctx, grupo, pendiente.desde())[0];
            }
            jdbc.update("UPDATE cambios_detectados SET estado = 'APLICADO', fecha_aplicacion = CURRENT_TIMESTAMP WHERE id = ?",
                    pendiente.id());
        }
        return Map.of("aplicados", pendientes.size(), "filasRecalculadas", filas);
    }

    private int[] calcularGrupo(Ctx ctx, List<ContratoRow> filas, LocalDate desde) {
        if (filas.isEmpty()) return new int[]{0, 0};
        double promedio = filas.stream().mapToDouble(ContratoRow::horas).average().orElse(0);
        double asignaciones = ctx.asignaciones().getOrDefault(filas.get(0).personaId(), 0.0);
        List<Object[]> lote = new ArrayList<>();
        int sinRegla = 0;
        for (ContratoRow contrato : filas) {
            if (desde != null && contrato.periodo().isBefore(desde)) continue;
            Formula.Regla regla = ctx.reglas().get(contrato.cargo());
            if (regla == null) { sinRegla++; continue; }
            double[] grupal = contrato.codEsp() == null ? null : ctx.grupal().get(contrato.codEsp());
            double pctGrupal = grupal == null ? 0 : grupal[0];
            double pacientes = grupal == null ? 1 : (contrato.it() == 131 || contrato.it() == 81 ? grupal[1] : grupal[2]);
            Formula.Semanal semanal = Formula.semanal(regla, contrato.horas(), promedio, asignaciones,
                    pctGrupal, pacientes, ctx.minInd());
            double semanas = semanasDelMes(ctx, contrato.it(), contrato.periodo().getMonthValue());
            Formula.Mensual mensual = Formula.mensual(semanal, semanas, ctx.metaPct());
            double foja = contrato.horas() > 0 ? semanal.horasDispDirecta() / contrato.horas() : 0;
            lote.add(new Object[]{contrato.personaId(), contrato.it(), Date.valueOf(contrato.periodo()),
                    contrato.instituto(), contrato.subArea(), contrato.codEsp(), contrato.cargo(), contrato.horas(),
                    semanal.tramo(), semanal.horasNas(), semanal.horasDisp(), semanal.horasDispDirecta(),
                    semanal.atencDirectaSem(), semanal.atencTotalSem(), semanas, mensual.planDirecta(),
                    mensual.planIndirecta(), mensual.planTotal(), mensual.metaGestion(), foja});
        }
        if (!lote.isEmpty()) {
            String sql;
            if (dialect.isPostgres()) {
                sql = "INSERT INTO resultado_planificacion (persona_id,it,periodo,instituto,sub_area,cod_esp,cargo,"
                        + "horas_semanales,tramo,horas_nas_sem,horas_disp_sem,horas_disp_directa_sem,atenc_directa_sem,"
                        + "atenc_total_sem,semanas_mes,plan_directa,plan_indirecta,plan_total,meta_gestion,foja,calculado_en) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (persona_id,it,periodo) DO UPDATE SET instituto=EXCLUDED.instituto, "
                        + "sub_area=EXCLUDED.sub_area, cod_esp=EXCLUDED.cod_esp, cargo=EXCLUDED.cargo, "
                        + "horas_semanales=EXCLUDED.horas_semanales, tramo=EXCLUDED.tramo, "
                        + "horas_nas_sem=EXCLUDED.horas_nas_sem, horas_disp_sem=EXCLUDED.horas_disp_sem, "
                        + "horas_disp_directa_sem=EXCLUDED.horas_disp_directa_sem, "
                        + "atenc_directa_sem=EXCLUDED.atenc_directa_sem, atenc_total_sem=EXCLUDED.atenc_total_sem, "
                        + "semanas_mes=EXCLUDED.semanas_mes, plan_directa=EXCLUDED.plan_directa, "
                        + "plan_indirecta=EXCLUDED.plan_indirecta, plan_total=EXCLUDED.plan_total, "
                        + "meta_gestion=EXCLUDED.meta_gestion, foja=EXCLUDED.foja, calculado_en=CURRENT_TIMESTAMP";
            } else {
                sql = "MERGE INTO resultado_planificacion (persona_id,it,periodo,instituto,sub_area,cod_esp,cargo,"
                        + "horas_semanales,tramo,horas_nas_sem,horas_disp_sem,horas_disp_directa_sem,atenc_directa_sem,"
                        + "atenc_total_sem,semanas_mes,plan_directa,plan_indirecta,plan_total,meta_gestion,foja,calculado_en) "
                        + "KEY (persona_id,it,periodo) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)";
            }
            jdbc.batchUpdate(sql, lote);
        }
        return new int[]{lote.size(), sinRegla};
    }

    private double semanasDelMes(Ctx ctx, int it, int mes) {
        Double override = ctx.semanasIt().get(it + "|" + mes);
        return override != null ? override : ctx.semanasNac()[mes - 1];
    }

    private Ctx cargarContexto() {
        Map<String, Formula.Regla> reglas = new HashMap<>();
        jdbc.query("SELECT * FROM parametros_cargo", rs -> {
            double[] dir = new double[3], ind = new double[3], nas = new double[3];
            String[] prefijos = {"t40", "t22", "t0"};
            for (int tramo = 0; tramo < 3; tramo++) {
                Double d = (Double) rs.getObject(prefijos[tramo] + "_dir");
                Double i = (Double) rs.getObject(prefijos[tramo] + "_ind");
                Double n = (Double) rs.getObject(prefijos[tramo] + "_nas");
                if (d == null || i == null || n == null) {
                    dir[tramo] = dir[Math.max(0, tramo - 1)];
                    ind[tramo] = ind[Math.max(0, tramo - 1)];
                    nas[tramo] = nas[Math.max(0, tramo - 1)];
                } else {
                    dir[tramo] = d; ind[tramo] = i; nas[tramo] = n;
                }
            }
            reglas.put(rs.getString("cargo"), new Formula.Regla(rs.getInt("std_directa"), dir, ind, nas));
        });

        Map<String, double[]> grupal = new HashMap<>();
        jdbc.query("SELECT * FROM factor_grupal", rs -> {
            grupal.put(rs.getString("cod_esp"), new double[]{rs.getDouble("pct_grupal"),
                rs.getDouble("pacientes_san_con"), rs.getDouble("pacientes_otros")});
        });
        double[] semanas = new double[12];
        java.util.Arrays.fill(semanas, 4.2);
        jdbc.query("SELECT mes, semanas_habiles FROM calendario_mes", rs -> {
            semanas[rs.getInt("mes") - 1] = rs.getDouble("semanas_habiles");
        });
        Map<String, Double> semanasIt = new HashMap<>();
        jdbc.query("SELECT it, mes, semanas_habiles FROM calendario_it", rs -> {
            semanasIt.put(rs.getInt("it") + "|" + rs.getInt("mes"), rs.getDouble("semanas_habiles"));
        });
        Map<String, Double> generales = new HashMap<>();
        jdbc.query("SELECT clave, valor FROM parametros_generales", rs -> {
            generales.put(rs.getString("clave"), rs.getDouble("valor"));
        });
        Map<Integer, Double> asignaciones = new HashMap<>();
        jdbc.query("SELECT persona_id, SUM(horas_semanales) AS h FROM asignaciones_persona "
                + "WHERE estado = 'APROBADA' GROUP BY persona_id", rs -> {
                asignaciones.put(rs.getInt("persona_id"), rs.getDouble("h"));
            });
        return new Ctx(reglas, grupal, semanas, semanasIt, generales.getOrDefault("meta_gestion", 0.9),
                generales.getOrDefault("min_indirecta", 30.0).intValue(), asignaciones);
    }
}