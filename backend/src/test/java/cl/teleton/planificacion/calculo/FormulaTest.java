package cl.teleton.planificacion.calculo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cl.teleton.planificacion.calculo.Formula;
import org.junit.jupiter.api.Test;

/** Caso tomado de la fila de un Medico Fisiatra de 40 h con 2 h de Lider de Equipo en el Excel de referencia. */
class FormulaTest {

    private final Formula.Regla fisiatra = new Formula.Regla(40,
            new double[]{0.65, 0.65, 0.65}, new double[]{0.15, 0.15, 0.15}, new double[]{0.20, 0.20, 0.20});

    @Test
    void fisiatra40HorasEnero() {
        Formula.Semanal s = Formula.semanal(fisiatra, 40, 40, 2, 0, 1, 30);
        assertEquals(30.0, s.horasDisp(), 1e-9);
        assertEquals(36.0, s.atencDirectaSem(), 1e-9);   // Excel: 36 directas por semana
        assertEquals(47.0, s.atencTotalSem(), 1e-9);     // Excel: 47 en total por semana

        Formula.Mensual m = Formula.mensual(s, 4.2, 0.9);
        assertEquals(151, m.planDirecta());              // Excel: PlanAteDir1 = 151
        assertEquals(197, m.planTotal());                // Excel: PlanAteDir+Ind1 = 197
        assertEquals(177, m.metaGestion());              // Excel: MG_AteDir+Ind1 = 177
    }

    @Test
    void tramosSegunPromedio() {
        assertEquals(0, Formula.tramo(44));
        assertEquals(1, Formula.tramo(30));
        assertEquals(2, Formula.tramo(21.9));
    }

    @Test
    void sinHorasNoHayAtenciones() {
        Formula.Semanal s = Formula.semanal(fisiatra, 0, 0, 0, 0, 1, 30);
        assertEquals(0.0, s.atencTotalSem(), 1e-9);
    }
}
