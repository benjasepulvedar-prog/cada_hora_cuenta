package cl.teleton.planificacion.controller;

import cl.teleton.planificacion.service.CalculoService;
import cl.teleton.planificacion.util.Json;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/asignaciones")
public class AsignacionesController {

    public record NuevaAsignacion(Integer personaId, String nombre, String solicitadoPor, String justificacion) {}

    private final JdbcTemplate jdbc;
    private final CalculoService calculo;

    public AsignacionesController(JdbcTemplate jdbc, CalculoService calculo) {
        this.jdbc = jdbc;
        this.calculo = calculo;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(name = "personaId", required = false) Integer personaId) {
        if (personaId == null) return Json.camel(jdbc.queryForList("SELECT * FROM asignaciones_persona ORDER BY id DESC LIMIT 200"));
        return Json.camel(jdbc.queryForList("SELECT * FROM asignaciones_persona WHERE persona_id = ? ORDER BY id DESC", personaId));
    }

    @PostMapping
    public Map<String, Object> crear(@RequestBody NuevaAsignacion body) {
        if (body.personaId() == null || body.nombre() == null || body.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personaId y nombre son obligatorios");
        }
        Double maxHoras = jdbc.queryForObject("SELECT MAX(horas_semanales) FROM contratos WHERE persona_id = ? AND vigente = TRUE",
                Double.class, body.personaId());
        if (maxHoras == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Profesional no encontrado: " + body.personaId());
        List<Map<String, Object>> cat = jdbc.queryForList(
                "SELECT nombre, horas_semanales, requiere_autorizacion FROM catalogo_asignaciones WHERE UPPER(nombre) = UPPER(?)",
                body.nombre().trim());
        if (cat.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La asignacion no esta en el catalogo. Ver GET /api/parametros/asignaciones");
        Map<String, Object> a = cat.get(0);

        if (maxHoras > 30) {
            Double max = jdbc.queryForObject("SELECT valor FROM parametros_generales WHERE clave = 'max_asignaciones_30h'", Double.class);
            Integer existentes = jdbc.queryForObject("SELECT COUNT(*) FROM asignaciones_persona WHERE persona_id = ?",
                    Integer.class, body.personaId());
            if (max != null && existentes != null && existentes >= max) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un profesional con mas de 30 h semanales puede tener como maximo " + max.intValue() + " asignaciones");
        }
        boolean requiere = Boolean.TRUE.equals(a.get("REQUIERE_AUTORIZACION"));
        String estado = requiere ? "PENDIENTE_AUTORIZACION" : "APROBADA";
        jdbc.update("INSERT INTO asignaciones_persona (persona_id,nombre,horas_semanales,estado,solicitado_por,justificacion) "
                        + "VALUES (?,?,?,?,?,?)", body.personaId(), a.get("NOMBRE"),
                ((Number) a.get("HORAS_SEMANALES")).doubleValue(), estado, body.solicitadoPor(), body.justificacion());
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM asignaciones_persona WHERE persona_id = ?", Long.class, body.personaId());
        if (!requiere) calculo.recalcularPersona(body.personaId(), null, null);
        return Json.camel(jdbc.queryForMap("SELECT * FROM asignaciones_persona WHERE id = ?", id));
    }

    @PostMapping("/{id}/aprobar")
    public Map<String, Object> aprobar(@PathVariable("id") long id) {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT persona_id FROM asignaciones_persona WHERE id = ?", id);
        if (filas.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignacion no encontrada");
        jdbc.update("UPDATE asignaciones_persona SET estado = 'APROBADA' WHERE id = ?", id);
        calculo.recalcularPersona(((Number) filas.get(0).get("PERSONA_ID")).intValue(), null, null);
        return Json.camel(jdbc.queryForMap("SELECT * FROM asignaciones_persona WHERE id = ?", id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> eliminar(@PathVariable("id") long id) {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT persona_id FROM asignaciones_persona WHERE id = ?", id);
        if (filas.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignacion no encontrada");
        jdbc.update("DELETE FROM asignaciones_persona WHERE id = ?", id);
        calculo.recalcularPersona(((Number) filas.get(0).get("PERSONA_ID")).intValue(), null, null);
        return Map.of("eliminada", id);
    }
}