package cl.teleton.planificacion.repository;

import cl.teleton.planificacion.util.Json;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consultas de auditoria relacionadas con las cargas de Excel. */
@Repository
public class CargaRepository {

    private final JdbcTemplate jdbc;

    public CargaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> ejecucionesRecientes() {
        return Json.camel(jdbc.queryForList("SELECT id, proceso, archivo, resultado, detalle, filas_leidas, filas_validas, "
                + "errores, cambios, fecha FROM log_ejecucion ORDER BY id DESC LIMIT 10"));
    }

    public List<Map<String, Object>> erroresRecientes(int limit) {
        return Json.camel(jdbc.queryForList("SELECT id, archivo, fila, persona_id, campo, mensaje, fecha "
                + "FROM errores_carga ORDER BY id DESC LIMIT ?", Math.min(Math.max(limit, 1), 1000)));
    }
}