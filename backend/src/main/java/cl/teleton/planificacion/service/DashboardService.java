package cl.teleton.planificacion.service;

import cl.teleton.planificacion.model.DashboardFilter;
import cl.teleton.planificacion.repository.DashboardRepository;
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
}