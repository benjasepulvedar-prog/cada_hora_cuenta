package cl.teleton.planificacion.controller;

import cl.teleton.planificacion.service.CalculoService;
import cl.teleton.planificacion.util.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CalculoController {

    private final CalculoService calculo;
    private final JdbcTemplate jdbc;

    public CalculoController(CalculoService calculo, JdbcTemplate jdbc) {
        this.calculo = calculo;
        this.jdbc = jdbc;
    }

    @PostMapping("/calculo/recalcular")
    public Map<String, Object> recalcular() { return calculo.recalcularTodo(); }

    @PostMapping("/calculo/pendientes")
    public Map<String, Object> pendientes() { return calculo.aplicarPendientes(); }

    @GetMapping("/cambios")
    public List<Map<String, Object>> cambios(@RequestParam(name = "estado", required = false) String estado,
                                             @RequestParam(name = "tipo", required = false) String tipo,
                                             @RequestParam(name = "personaId", required = false) Integer personaId,
                                             @RequestParam(name = "limit", defaultValue = "200") int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, persona_id, it, tipo, detalle, periodo_desde, estado, archivo, fecha_deteccion, fecha_aplicacion "
                        + "FROM cambios_detectados WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (estado != null) { sql.append("AND estado = ? "); args.add(estado.toUpperCase()); }
        if (tipo != null) { sql.append("AND tipo = ? "); args.add(tipo.toUpperCase()); }
        if (personaId != null) { sql.append("AND persona_id = ? "); args.add(personaId); }
        sql.append("ORDER BY id DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 1000));
        return Json.camel(jdbc.queryForList(sql.toString(), args.toArray()));
    }
}