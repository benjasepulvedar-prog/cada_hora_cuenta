package cl.teleton.planificacion.controller;

import cl.teleton.planificacion.model.DashboardFilter;
import cl.teleton.planificacion.service.DashboardService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API del dashboard; delega reglas y consultas en service/repository. */
@RestController
@RequestMapping("/api/resumen")
public class ResumenController {

    private final DashboardService dashboard;

    public ResumenController(DashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping
    public List<Map<String, Object>> resumen(@RequestParam(name = "it", required = false) Integer it,
                                             @RequestParam(name = "mes", required = false) Integer mes,
                                             @RequestParam(name = "area", required = false) String area,
                                             @RequestParam(name = "cargo", required = false) String cargo,
                                             @RequestParam(name = "codEsp", required = false) String codEsp,
                                             @RequestParam(name = "personaId", required = false) Integer personaId,
                                             @RequestParam(name = "agruparPor", defaultValue = "it") String agruparPor) {
        return dashboard.resumen(new DashboardFilter(it, mes, area, cargo, codEsp, personaId, agruparPor));
    }

    @GetMapping("/nacional")
    public Map<String, Object> nacional(@RequestParam(name = "it", required = false) Integer it,
                                        @RequestParam(name = "mes", required = false) Integer mes,
                                        @RequestParam(name = "area", required = false) String area,
                                        @RequestParam(name = "cargo", required = false) String cargo,
                                        @RequestParam(name = "codEsp", required = false) String codEsp) {
        return dashboard.nacional(new DashboardFilter(it, mes, area, cargo, codEsp, null, null));
    }

    @GetMapping("/profesional/{personaId}")
    public List<Map<String, Object>> profesional(@PathVariable("personaId") int personaId) {
        return dashboard.profesional(personaId);
    }

    @GetMapping("/detalle")
    public List<Map<String, Object>> detalle(@RequestParam(name = "it", required = false) Integer it,
                                             @RequestParam(name = "mes", required = false) Integer mes,
                                             @RequestParam(name = "area", required = false) String area,
                                             @RequestParam(name = "cargo", required = false) String cargo,
                                             @RequestParam(name = "codEsp", required = false) String codEsp,
                                             @RequestParam(name = "personaId", required = false) Integer personaId) {
        return dashboard.detalle(new DashboardFilter(it, mes, area, cargo, codEsp, personaId, null));
    }

    @GetMapping(value = "/exportar.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportarCsv(@RequestParam(name = "it", required = false) Integer it,
                                              @RequestParam(name = "mes", required = false) Integer mes,
                                              @RequestParam(name = "area", required = false) String area,
                                              @RequestParam(name = "cargo", required = false) String cargo,
                                              @RequestParam(name = "codEsp", required = false) String codEsp,
                                              @RequestParam(name = "personaId", required = false) Integer personaId) {
        byte[] csv = dashboard.csv(new DashboardFilter(it, mes, area, cargo, codEsp, personaId, null));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=planificacion.csv")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv);
    }
}