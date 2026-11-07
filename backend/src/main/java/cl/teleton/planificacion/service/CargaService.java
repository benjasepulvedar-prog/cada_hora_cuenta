package cl.teleton.planificacion.service;

import cl.teleton.planificacion.model.ContratoRow;
import cl.teleton.planificacion.model.FilaBuk;
import cl.teleton.planificacion.model.ResultadoCarga;
import cl.teleton.planificacion.model.ResultadoCarga.ErrorFila;
import cl.teleton.planificacion.repository.ContratoRepository;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Valida el reporte, detecta diferencias y persiste las filas aceptadas. */
@Service
public class CargaService {

    private static final int MAX_ERRORES_GUARDADOS = 500;
    private static final int MAX_ERRORES_RESPUESTA = 25;

    private final ExcelBukReader reader;
    private final ContratoRepository repo;
    private final JdbcTemplate jdbc;

    public CargaService(ExcelBukReader reader, ContratoRepository repo, JdbcTemplate jdbc) {
        this.reader = reader;
        this.repo = repo;
        this.jdbc = jdbc;
    }

    private record Cambio(int personaId, int it, String tipo, String detalle, LocalDate desde) {}

    @Transactional
    public ResultadoCarga procesar(Path archivo, String nombre) {
        ExcelBukReader.Lectura lec;
        try {
            lec = reader.leer(archivo);
        } catch (Exception e) {
            String msg = "No se pudo leer el archivo: " + e.getMessage();
            registrarLog(nombre, "ERROR", msg, 0, 0, 0, 0);
            return new ResultadoCarga(nombre, "ERROR", false, 0, 0, 1,
                    List.of(new ErrorFila(0, null, "archivo", msg)), Map.of(), msg);
        }
        if (!lec.erroresEstructura().isEmpty()) {
            String msg = String.join("; ", lec.erroresEstructura());
            registrarLog(nombre, "RECHAZADO", msg, 0, 0, lec.erroresEstructura().size(), 0);
            List<ErrorFila> errs = lec.erroresEstructura().stream()
                    .map(m -> new ErrorFila(0, null, "estructura", m)).toList();
            return new ResultadoCarga(nombre, "RECHAZADO", false, 0, 0, errs.size(), errs, Map.of(), msg);
        }

        Set<String> cargos = new HashSet<>(jdbc.queryForList("SELECT cargo FROM parametros_cargo", String.class));
        List<FilaBuk> validas = new ArrayList<>();
        List<ErrorFila> errores = new ArrayList<>();
        Set<String> vistos = new HashSet<>();

        for (FilaBuk f : lec.filas()) {
            List<ErrorFila> deFila = new ArrayList<>();
            for (String p : f.problemas()) deFila.add(new ErrorFila(f.fila(), f.personaId(), "formato", p));
            if (f.horas() != null && (f.horas() < 0 || f.horas() > 60)) {
                deFila.add(new ErrorFila(f.fila(), f.personaId(), "Horas Semanales", "Fuera de rango (0 a 60): " + f.horas()));
            }
            if (f.cargo() == null || f.cargo().isBlank()) {
                deFila.add(new ErrorFila(f.fila(), f.personaId(), "Cargo", "Cargo vacio"));
            } else if (!cargos.contains(f.cargo())) {
                deFila.add(new ErrorFila(f.fila(), f.personaId(), "Cargo", "Cargo no homologado: " + f.cargo()));
            }
            if (deFila.isEmpty() && !vistos.add(f.llave())) {
                deFila.add(new ErrorFila(f.fila(), f.personaId(), "llave", "Fila duplicada (PersonaId + IT + Periodo)"));
            }
            if (deFila.isEmpty()) validas.add(f); else errores.addAll(deFila);
        }

        Map<String, ContratoRow> db = repo.todos();
        boolean inicial = db.isEmpty();
        List<Cambio> cambios = inicial ? List.of() : detectarCambios(validas, db);

        repo.upsert(validas, nombre);
        LocalDate maxPeriodo = validas.stream().map(FilaBuk::periodo).max(Comparator.naturalOrder()).orElse(null);
        for (Cambio c : cambios) {
            if (c.tipo().equals("BAJA")) repo.marcarNoVigente(c.personaId(), c.it(), c.desde());
            jdbc.update("INSERT INTO cambios_detectados (persona_id,it,tipo,detalle,periodo_desde,estado,archivo) "
                            + "VALUES (?,?,?,?,?, 'PENDIENTE', ?)", c.personaId(), c.it(), c.tipo(), c.detalle(),
                    Date.valueOf(c.desde()), nombre);
        }
        int guardados = 0;
        for (ErrorFila e : errores) {
            if (guardados++ >= MAX_ERRORES_GUARDADOS) break;
            jdbc.update("INSERT INTO errores_carga (archivo,fila,persona_id,campo,mensaje) VALUES (?,?,?,?,?)",
                    nombre, e.fila(), e.personaId(), e.campo(), e.mensaje());
        }

        Map<String, Integer> porTipo = new TreeMap<>();
        for (Cambio c : cambios) porTipo.merge(c.tipo(), 1, Integer::sum);
        String estado = errores.isEmpty() ? "OK" : "OK_CON_ERRORES";
        String msg = inicial
                ? "Carga inicial: " + validas.size() + " filas. Aun no hay version anterior con la cual comparar."
                : cambios.size() + " cambios detectados" + (maxPeriodo == null ? "" : " (reporte vigente a " + maxPeriodo + ")");
        registrarLog(nombre, estado, msg, lec.filas().size(), validas.size(), errores.size(), cambios.size());

        return new ResultadoCarga(nombre, estado, inicial, lec.filas().size(), validas.size(), errores.size(),
                errores.stream().limit(MAX_ERRORES_RESPUESTA).toList(), porTipo, msg);
    }

    private List<Cambio> detectarCambios(List<FilaBuk> validas, Map<String, ContratoRow> db) {
        Map<String, List<FilaBuk>> porPersona = validas.stream()
                .collect(Collectors.groupingBy(f -> f.personaId() + "|" + f.it(), LinkedHashMap::new, Collectors.toList()));
        Set<String> personasDb = db.values().stream().filter(ContratoRow::vigente)
                .map(c -> c.personaId() + "|" + c.it()).collect(Collectors.toSet());
        LocalDate maxPeriodo = validas.stream().map(FilaBuk::periodo).max(Comparator.naturalOrder()).orElse(null);

        List<Cambio> out = new ArrayList<>();
        for (Map.Entry<String, List<FilaBuk>> e : porPersona.entrySet()) {
            List<FilaBuk> filas = e.getValue();
            FilaBuk primera = filas.get(0);
            if (!personasDb.contains(e.getKey())) {
                LocalDate desde = filas.stream().map(FilaBuk::periodo).min(Comparator.naturalOrder()).orElseThrow();
                out.add(new Cambio(primera.personaId(), primera.it(), "ALTA", "Nuevo en el reporte (" + primera.cargo() + ")", desde));
                continue;
            }
            Map<String, Cambio> porTipo = new LinkedHashMap<>();
            for (FilaBuk f : filas) {
                ContratoRow o = db.get(f.llave());
                if (o == null) { acumular(porTipo, f, "PERIODO_NUEVO", "Mes nuevo en el reporte"); continue; }
                if (Math.abs(o.horas() - f.horas()) > 0.001) acumular(porTipo, f, "JORNADA", o.horas() + " -> " + f.horas() + " h/sem");
                if (!o.cargo().equals(f.cargo())) acumular(porTipo, f, "CARGO", o.cargo() + " -> " + f.cargo());
                if (!Objects.equals(o.tipoContrato(), f.tipoContrato()) || !Objects.equals(o.tipoTrabajador(), f.tipoTrabajador())) {
                    acumular(porTipo, f, "CONTRATO", o.tipoContrato() + "/" + o.tipoTrabajador() + " -> "
                            + f.tipoContrato() + "/" + f.tipoTrabajador());
                }
                if (!Objects.equals(o.fechaTermino(), f.fechaTermino())) {
                    acumular(porTipo, f, "VIGENCIA", "Termino: " + o.fechaTermino() + " -> " + f.fechaTermino());
                }
            }
            out.addAll(porTipo.values());
        }
        if (maxPeriodo != null) {
            for (String pk : personasDb) {
                if (porPersona.containsKey(pk)) continue;
                String[] p = pk.split("\\|");
                out.add(new Cambio(Integer.parseInt(p[0]), Integer.parseInt(p[1]), "BAJA",
                        "No aparece en el reporte vigente a " + maxPeriodo, maxPeriodo));
            }
        }
        return out;
    }

    private void acumular(Map<String, Cambio> mapa, FilaBuk f, String tipo, String detalle) {
        Cambio actual = mapa.get(tipo);
        if (actual == null || f.periodo().isBefore(actual.desde())) {
            mapa.put(tipo, new Cambio(f.personaId(), f.it(), tipo, detalle + " (desde " + f.periodo() + ")", f.periodo()));
        }
    }

    private void registrarLog(String archivo, String resultado, String detalle, int leidas, int validas, int errores, int cambios) {
        jdbc.update("INSERT INTO log_ejecucion (proceso,archivo,resultado,detalle,filas_leidas,filas_validas,errores,cambios) "
                        + "VALUES (?,?,?,?,?,?,?,?)", "Procesar Reporte BUK", archivo, resultado,
                detalle.length() > 490 ? detalle.substring(0, 490) : detalle, leidas, validas, errores, cambios);
    }
}