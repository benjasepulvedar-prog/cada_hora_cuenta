package cl.teleton.planificacion.controller;

import cl.teleton.planificacion.bot.BotService;
import cl.teleton.planificacion.util.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SaludController {

    private final JdbcTemplate jdbc;
    private final BotService bot;

    public SaludController(JdbcTemplate jdbc, BotService bot) {
        this.jdbc = jdbc;
        this.bot = bot;
    }

    @GetMapping("/salud")
    public Map<String, Object> salud() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("estado", "UP");
        m.put("modoBot", bot.modo());
        m.put("contratos", jdbc.queryForObject("SELECT COUNT(*) FROM contratos", Integer.class));
        m.put("resultadosCalculados", jdbc.queryForObject("SELECT COUNT(*) FROM resultado_planificacion", Integer.class));
        m.put("cambiosPendientes", jdbc.queryForObject("SELECT COUNT(*) FROM cambios_detectados WHERE estado = 'PENDIENTE'", Integer.class));
        m.put("cargosParametrizados", jdbc.queryForObject("SELECT COUNT(*) FROM parametros_cargo", Integer.class));
        return m;
    }

    @GetMapping("/institutos")
    public List<Map<String, Object>> institutos() {
        return Json.camel(jdbc.queryForList("SELECT DISTINCT it, instituto FROM contratos ORDER BY it"));
    }
}