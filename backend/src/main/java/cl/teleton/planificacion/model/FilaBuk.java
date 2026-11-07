package cl.teleton.planificacion.model;

import java.time.LocalDate;
import java.util.List;

/** Una fila del reporte de BUK (una persona, un instituto, un mes). */
public record FilaBuk(int fila, Integer personaId, Integer it, String instituto, String ciudad, String subArea,
                      String codEsp, LocalDate periodo, String tipoContrato, String tipoTrabajador,
                      LocalDate fechaTermino, Double horas, String cargo, String regSic, List<String> problemas) {

    public String llave() {
        return personaId + "|" + it + "|" + periodo;
    }
}