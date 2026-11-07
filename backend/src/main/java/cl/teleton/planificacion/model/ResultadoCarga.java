package cl.teleton.planificacion.model;

import java.util.List;
import java.util.Map;

public record ResultadoCarga(String archivo, String estado, boolean cargaInicial, int filasLeidas, int filasValidas,
                             int totalErrores, List<ErrorFila> primerosErrores, Map<String, Integer> cambiosPorTipo,
                             String mensaje) {

    public record ErrorFila(int fila, Integer personaId, String campo, String mensaje) {}
}