package cl.teleton.planificacion.repository;

import cl.teleton.planificacion.model.DashboardFilter;
import cl.teleton.planificacion.config.DatabaseDialect;
import cl.teleton.planificacion.util.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acceso a los resultados persistidos que alimentan el dashboard. */
@Repository
public class DashboardRepository {

    private static final Map<String, String> GRUPOS = Map.of(
            "it", "r.it",
            "instituto", "r.instituto",
            "area", "r.sub_area",
            "cargo", "r.cargo",
            "codEsp", "r.cod_esp",
            "mes", "MONTH(r.periodo)",
            "profesional", "r.persona_id");

    private static final String METRICAS = "COUNT(DISTINCT r.persona_id) AS profesionales, "
            + "SUM(r.plan_directa) AS plan_directa, SUM(r.plan_indirecta) AS plan_indirecta, "
            + "SUM(r.plan_total) AS plan_total, SUM(r.meta_gestion) AS meta_gestion, "
            + "SUM(r.horas_disp_directa_sem) / NULLIF(SUM(r.horas_semanales), 0) AS foja ";

    private final JdbcTemplate jdbc;
        private final DatabaseDialect dialect;

        public DashboardRepository(JdbcTemplate jdbc, DatabaseDialect dialect) {
                this.jdbc = jdbc;
                this.dialect = dialect;
        }

    public boolean admiteAgrupacion(String agruparPor) { return GRUPOS.containsKey(agruparPor); }

    public List<Map<String, Object>> resumen(DashboardFilter filtro) {
        String col = filtro.agruparPor().equals("mes") ? dialect.month("r.periodo") : GRUPOS.get(filtro.agruparPor());
        Consulta consulta = filtro(filtro);
        String sql = "SELECT " + col + " AS clave, " + METRICAS + "FROM resultado_planificacion r " + consulta.where()
                + "GROUP BY " + col + " ORDER BY " + col;
        return Json.camel(jdbc.queryForList(sql, consulta.args().toArray()));
    }

    public Map<String, Object> nacional(DashboardFilter filtro) {
        Consulta consulta = filtro(filtro);
        String month = dialect.month("r.periodo");
        Map<String, Object> totales = Json.camel(jdbc.queryForMap(
                "SELECT " + METRICAS + "FROM resultado_planificacion r " + consulta.where(), consulta.args().toArray()));
        List<Map<String, Object>> porMes = Json.camel(jdbc.queryForList(
                "SELECT " + month + " AS mes, " + METRICAS + "FROM resultado_planificacion r " + consulta.where()
                        + "GROUP BY " + month + " ORDER BY " + month, consulta.args().toArray()));
        return Map.of("totales", totales, "porMes", porMes);
    }

    public List<Map<String, Object>> profesional(int personaId) {
        return Json.camel(jdbc.queryForList(
                "SELECT * FROM resultado_planificacion WHERE persona_id = ? ORDER BY it, periodo", personaId));
    }

    private record Consulta(String where, List<Object> args) {}

    private Consulta filtro(DashboardFilter f) {
        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (f.it() != null) { where.append("AND r.it = ? "); args.add(f.it()); }
        if (f.mes() != null) { where.append("AND " + dialect.month("r.periodo") + " = ? "); args.add(f.mes()); }
        if (f.area() != null) { where.append("AND UPPER(r.sub_area) = UPPER(?) "); args.add(f.area()); }
        if (f.cargo() != null) { where.append("AND UPPER(r.cargo) = UPPER(?) "); args.add(f.cargo()); }
        if (f.codEsp() != null) { where.append("AND UPPER(r.cod_esp) = UPPER(?) "); args.add(f.codEsp()); }
        if (f.personaId() != null) { where.append("AND r.persona_id = ? "); args.add(f.personaId()); }
        return new Consulta(where.toString(), args);
    }
}