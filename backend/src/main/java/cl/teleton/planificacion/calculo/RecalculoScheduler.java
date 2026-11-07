package cl.teleton.planificacion.calculo;

import cl.teleton.planificacion.service.CalculoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Aplica periodicamente los cambios PENDIENTES que el bot deja en la base de datos. */
@Component
public class RecalculoScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecalculoScheduler.class);
    private final CalculoService calculo;

    public RecalculoScheduler(CalculoService calculo) { this.calculo = calculo; }

    @Scheduled(fixedDelayString = "${app.recalculo.intervalo-ms:15000}", initialDelay = 20000)
    public void aplicar() {
        try {
            var r = calculo.aplicarPendientes();
            if (!Integer.valueOf(0).equals(r.get("aplicados"))) log.info("Cambios aplicados: {}", r);
        } catch (Exception e) {
            log.warn("No se pudieron aplicar cambios pendientes: {}", e.getMessage());
        }
    }
}
