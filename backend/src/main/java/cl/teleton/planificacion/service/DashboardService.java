package cl.teleton.planificacion.service;

import cl.teleton.planificacion.model.DashboardFilter;
import cl.teleton.planificacion.repository.DashboardRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Reglas de consulta para los datos agregados que consume el dashboard. */
@Service
public class DashboardService {

    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) { this.repository = repository; }

    public List<Map<String, Object>> resumen(DashboardFilter filtro) {
        if (!repository.admiteAgrupacion(filtro.agruparPor())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "agruparPor invalido. Opciones: it, instituto, area, cargo, codEsp, mes, profesional");
        }
        return repository.resumen(filtro);
    }

    public Map<String, Object> nacional(DashboardFilter filtro) { return repository.nacional(filtro); }

    public List<Map<String, Object>> profesional(int personaId) { return repository.profesional(personaId); }

    public List<Map<String, Object>> detalle(DashboardFilter filtro) { return repository.detalle(filtro); }

    public byte[] csv(DashboardFilter filtro) {
        StringBuilder csv = new StringBuilder("\uFEFFPersonaId;IT;Ciudad;Instituto;Subarea;CodEsp;Cargo;Periodo;"
                + "HorasSemanales;PlanDirecta;PlanIndirecta;PlanTotal;MetaGestion;Foja\r\n");
        for (Map<String, Object> row : repository.detalle(filtro)) {
            append(csv, row.get("personaId"));
            append(csv, row.get("it"));
            append(csv, row.get("ciudad"));
            append(csv, row.get("instituto"));
            append(csv, row.get("subArea"));
            append(csv, row.get("codEsp"));
            append(csv, row.get("cargo"));
            append(csv, row.get("periodo"));
            append(csv, row.get("horasSemanales"));
            append(csv, row.get("planDirecta"));
            append(csv, row.get("planIndirecta"));
            append(csv, row.get("planTotal"));
            append(csv, row.get("metaGestion"));
            appendLast(csv, row.get("foja"));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void append(StringBuilder csv, Object value) {
        csv.append(csvValue(value)).append(';');
    }

    private void appendLast(StringBuilder csv, Object value) {
        csv.append(csvValue(value)).append("\r\n");
    }

    private String csvValue(Object value) {
        if (value == null) return "";
        String text = value.toString();
        if (text.matches("^[=+@\\-\\t\\r].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}