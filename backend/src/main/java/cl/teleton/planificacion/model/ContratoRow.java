package cl.teleton.planificacion.model;

import java.time.LocalDate;

/** Fila de la tabla contratos (la ultima version cargada). */
public record ContratoRow(int personaId, int it, LocalDate periodo, String instituto, String subArea, String codEsp,
                          String tipoContrato, String tipoTrabajador, LocalDate fechaTermino, double horas,
                          String cargo, boolean vigente) {

    public String llave() {
        return personaId + "|" + it + "|" + periodo;
    }
}