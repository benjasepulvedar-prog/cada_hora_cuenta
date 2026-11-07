package cl.teleton.planificacion.controller;

import cl.teleton.planificacion.service.CalculoService;
import cl.teleton.planificacion.util.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/parametros")
public class ParametrosController {

    private static final Map<String, String> COLS_CARGO = Map.ofEntries(
            Map.entry("stdDirecta", "std_directa"), Map.entry("stdIndirecta", "std_indirecta"),
            Map.entry("t40Dir", "t40_dir"), Map.entry("t40Ind", "t40_ind"), Map.entry("t40Nas", "t40_nas"),
            Map.entry("t22Dir", "t22_dir"), Map.entry("t22Ind", "t22_ind"), Map.entry("t22Nas", "t22_nas"),
            Map.entry("t0Dir", "t0_dir"), Map.entry("t0Ind", "t0_ind"), Map.entry("t0Nas", "t0_nas"));

    private final JdbcTemplate jdbc;
    private final CalculoService calculo;

    public ParametrosController(JdbcTemplate jdbc, CalculoService calculo) {
        this.jdbc = jdbc;
        this.calculo = calculo;
    }

    @GetMapping("/cargos")
    public List<Map<String, Object>> cargos(@RequestParam(name = "q", required = false) String q) {
        if (q == null || q.isBlank()) return Json.camel(jdbc.queryForList("SELECT * FROM parametros_cargo ORDER BY cargo"));
        return Json.camel(jdbc.queryForList("SELECT * FROM parametros_cargo WHERE UPPER(cargo) LIKE ? ORDER BY cargo",
                "%" + q.toUpperCase() + "%"));
    }

    @PutMapping("/cargos/{cargo}")
    @Transactional
    public Map<String, Object> actualizarCargo(@PathVariable("cargo") String cargo,
                                               @RequestBody Map<String, Number> cambios,
                                               @RequestParam(name = "recalcular", defaultValue = "false") boolean recalcular) {
        String clave = cargo.trim().toUpperCase();
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Number> e : cambios.entrySet()) {
            String col = COLS_CARGO.get(e.getKey());
            if (col == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Campo no permitido: " + e.getKey() + ". Permitidos: " + COLS_CARGO.keySet());
            sets.add(col + " = ?");
            args.add(col.startsWith("std") ? (Object) e.getValue().intValue() : (Object) e.getValue().doubleValue());
        }
        if (sets.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se envio ningun campo");
        args.add(clave);
        int n = jdbc.update("UPDATE parametros_cargo SET " + String.join(", ", sets) + " WHERE cargo = ?", args.toArray());
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cargo no encontrado: " + clave);

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM parametros_cargo WHERE cargo = ?", clave);
        for (String t : new String[]{"T40", "T22", "T0"}) {
            Object d = fila.get(t + "_DIR"), i = fila.get(t + "_IND"), s = fila.get(t + "_NAS");
            if (d != null && i != null && s != null) {
                double suma = ((Number) d).doubleValue() + ((Number) i).doubleValue() + ((Number) s).doubleValue();
                if (Math.abs(suma - 1.0) > 0.01) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El tramo " + t + " debe sumar 100% (directo + indirecto + NAS) y suma " + suma);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>(Json.camel(fila));
        if (recalcular) out.put("recalculo", calculo.recalcularTodo());
        return out;
    }

    public record Valor(Double valor) {}
    public record Semanas(Double semanasHabiles) {}

    @GetMapping("/generales")
    public List<Map<String, Object>> generales() {
        return Json.camel(jdbc.queryForList("SELECT * FROM parametros_generales ORDER BY clave"));
    }

    @PutMapping("/generales/{clave}")
    @Transactional
    public Map<String, Object> actualizarGeneral(@PathVariable("clave") String clave, @RequestBody Valor body,
                                                 @RequestParam(name = "recalcular", defaultValue = "false") boolean recalcular) {
        if (body.valor() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el campo valor");
        int n = jdbc.update("UPDATE parametros_generales SET valor = ? WHERE clave = ?", body.valor(), clave);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parametro no encontrado: " + clave);
        Map<String, Object> out = new LinkedHashMap<>(Json.camel(
                jdbc.queryForMap("SELECT * FROM parametros_generales WHERE clave = ?", clave)));
        if (recalcular) out.put("recalculo", calculo.recalcularTodo());
        return out;
    }

    @GetMapping("/calendario")
    public Map<String, Object> calendario() {
        return Map.of("nacional", Json.camel(jdbc.queryForList("SELECT * FROM calendario_mes ORDER BY mes")),
                "porInstituto", Json.camel(jdbc.queryForList("SELECT * FROM calendario_it ORDER BY it, mes")));
    }

    @PutMapping("/calendario/{mes}")
    @Transactional
    public Map<String, Object> actualizarMes(@PathVariable("mes") int mes, @RequestBody Semanas body,
                                             @RequestParam(name = "recalcular", defaultValue = "false") boolean recalcular) {
        validar(mes, body);
        jdbc.update("MERGE INTO calendario_mes (mes, semanas_habiles) KEY (mes) VALUES (?, ?)", mes, body.semanasHabiles());
        Map<String, Object> out = new LinkedHashMap<>(Map.of("mes", mes, "semanasHabiles", body.semanasHabiles()));
        if (recalcular) out.put("recalculo", calculo.recalcularTodo());
        return out;
    }

    @PutMapping("/calendario/instituto/{it}/{mes}")
    @Transactional
    public Map<String, Object> actualizarMesInstituto(@PathVariable("it") int it, @PathVariable("mes") int mes,
                                                      @RequestBody Semanas body,
                                                      @RequestParam(name = "recalcular", defaultValue = "false") boolean recalcular) {
        validar(mes, body);
        jdbc.update("MERGE INTO calendario_it (it, mes, semanas_habiles) KEY (it, mes) VALUES (?, ?, ?)",
                it, mes, body.semanasHabiles());
        Map<String, Object> out = new LinkedHashMap<>(Map.of("it", it, "mes", mes, "semanasHabiles", body.semanasHabiles()));
        if (recalcular) out.put("recalculo", calculo.recalcularTodo());
        return out;
    }

    private void validar(int mes, Semanas body) {
        if (mes < 1 || mes > 12) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mes debe estar entre 1 y 12");
        if (body.semanasHabiles() == null || body.semanasHabiles() < 0 || body.semanasHabiles() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "semanasHabiles debe estar entre 0 y 5");
        }
    }

    @GetMapping("/asignaciones")
    public List<Map<String, Object>> catalogoAsignaciones() {
        return Json.camel(jdbc.queryForList("SELECT * FROM catalogo_asignaciones ORDER BY nombre"));
    }
}