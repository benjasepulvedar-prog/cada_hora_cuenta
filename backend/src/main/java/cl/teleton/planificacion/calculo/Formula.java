package cl.teleton.planificacion.calculo;

/**
 * Reglas de calculo puras (sin Spring ni BD) para poder probarlas facilmente.
 * Replican la logica de la hoja Dotacion_Asistencial del Excel de referencia.
 */
public final class Formula {

    private Formula() {}

    private static final double EPS = 1e-9;

    /** Parametros de un cargo. Los arreglos van en orden de tramo: [0] >=40 h, [1] 22-40 h, [2] <22 h. */
    public record Regla(int stdDirecta, double[] dir, double[] ind, double[] nas) {}

    /** Resultado por semana tipo de un mes. */
    public record Semanal(String tramo, double horasNas, double horasDisp, double horasDispDirecta,
                          double horasDispIndirecta, double atencDirectaSem, double atencTotalSem) {}

    /** Resultado mensual (semanas habiles del mes aplicadas). */
    public record Mensual(int planDirecta, int planIndirecta, int planTotal, int metaGestion) {}

    /** 0 = tramo >=40 h, 1 = entre 22 y 40 h, 2 = menos de 22 h (segun promedio de horas del profesional). */
    public static int tramo(double promedioHoras) {
        if (promedioHoras >= 40) return 0;
        if (promedioHoras >= 22) return 1;
        return 2;
    }

    static String nombreTramo(int t) {
        return switch (t) { case 0 -> "T40"; case 1 -> "T22_40"; default -> "T_MENOS22"; };
    }

    private static double piso(double x) { return Math.floor(x + EPS); }

    /**
     * @param horasMes     horas semanales contratadas en ese mes
     * @param promedio     promedio de horas semanales del profesional (define el tramo)
     * @param asignaciones horas semanales de asignaciones especiales aprobadas
     * @param pctGrupal    % de atencion grupal (0 si el area no aplica)
     * @param pacGrupo     pacientes por grupo (1 si no aplica)
     * @param minIndirecta minutos estandar de atencion indirecta
     */
    public static Semanal semanal(Regla r, double horasMes, double promedio, double asignaciones,
                                  double pctGrupal, double pacGrupo, int minIndirecta) {
        int t = tramo(promedio);
        if (horasMes <= 0) return new Semanal(nombreTramo(t), 0, 0, 0, 0, 0, 0);

        double nasHoras = horasMes * r.nas()[t];
        double disp = Math.max(0, horasMes - nasHoras - asignaciones);

        double base = r.dir()[t] + r.ind()[t];
        double shareDir = base <= 0 ? 0 : r.dir()[t] / base;
        double hDir = disp * shareDir;
        double hInd = disp - hDir;

        double atDir = piso(hDir * 60.0 / r.stdDirecta());
        double atInd = piso(hInd * 60.0 / minIndirecta);
        double factorGrupal = 1 - pctGrupal + pctGrupal * pacGrupo;   // 1 si no hay atencion grupal
        double atDirAjustada = atDir * factorGrupal;

        return new Semanal(nombreTramo(t), nasHoras, disp, hDir, hInd, atDirAjustada, atDirAjustada + atInd);
    }

    public static Mensual mensual(Semanal s, double semanasMes, double metaPct) {
        int planDir = (int) piso(s.atencDirectaSem() * semanasMes);
        int planTot = (int) piso(s.atencTotalSem() * semanasMes);
        int meta = (int) piso(planTot * metaPct);
        return new Mensual(planDir, Math.max(0, planTot - planDir), planTot, meta);
    }
}
