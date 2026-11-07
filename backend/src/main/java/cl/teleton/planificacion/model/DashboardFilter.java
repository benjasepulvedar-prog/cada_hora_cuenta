package cl.teleton.planificacion.model;

/** Filtros admitidos por las consultas de resumen del dashboard. */
public record DashboardFilter(Integer it, Integer mes, String area, String cargo, String codEsp, Integer personaId,
                              String agruparPor) {}