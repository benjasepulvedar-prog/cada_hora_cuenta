package cl.teleton.planificacion.service;

import cl.teleton.planificacion.model.FilaBuk;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

/** Lee el reporte BUK buscando encabezados por nombre, incluso si hay filas vacias al inicio. */
@Component
public class ExcelBukReader {

    public record Lectura(List<FilaBuk> filas, List<String> erroresEstructura) {}

    private static final List<String> REQUERIDAS = List.of("it", "personaid", "periodo", "horassemanales", "cargo", "codesp");
    private static final List<DateTimeFormatter> FORMATOS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"));

    public Lectura leer(Path archivo) throws IOException {
        try (InputStream in = Files.newInputStream(archivo); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sh = wb.getSheet("Dotacion_2026");
            if (sh == null) sh = wb.getSheetAt(0);

            int headerRow = -1;
            Map<String, Integer> cols = new HashMap<>();
            int tope = Math.min(sh.getLastRowNum(), sh.getFirstRowNum() + 15);
            for (int i = sh.getFirstRowNum(); i <= tope && headerRow < 0; i++) {
                Row r = sh.getRow(i);
                if (r == null) continue;
                Map<String, Integer> m = new HashMap<>();
                for (Cell c : r) {
                    String t = texto(valor(c));
                    if (t != null) m.put(normalizar(t), c.getColumnIndex());
                }
                if (m.containsKey("personaid")) {
                    headerRow = i;
                    cols = m;
                }
            }
            if (headerRow < 0) {
                return new Lectura(List.of(), List.of("No se encontro la fila de encabezados (columna PersonaId)"));
            }
            List<String> faltan = new ArrayList<>();
            for (String req : REQUERIDAS) if (!cols.containsKey(req)) faltan.add("Falta la columna obligatoria: " + req);
            if (!faltan.isEmpty()) return new Lectura(List.of(), faltan);

            List<FilaBuk> filas = new ArrayList<>();
            for (int i = headerRow + 1; i <= sh.getLastRowNum(); i++) {
                Row r = sh.getRow(i);
                if (r == null) continue;
                Object vPersona = valor(r.getCell(cols.get("personaid")));
                Object vIt = valor(r.getCell(cols.get("it")));
                if (vPersona == null && vIt == null) continue;

                List<String> prob = new ArrayList<>();
                Integer persona = aEntero(vPersona, "PersonaId", prob);
                Integer it = aEntero(vIt, "IT", prob);
                LocalDate periodo = aFecha(valor(r.getCell(cols.get("periodo"))), "Periodo", prob);
                LocalDate termino = cols.containsKey("fechatermino")
                        ? aFecha(valor(r.getCell(cols.get("fechatermino"))), "Fecha Termino", prob) : null;
                Double horas = aDecimal(valor(r.getCell(cols.get("horassemanales"))), "Horas Semanales", prob);
                String cargo = texto(valor(r.getCell(cols.get("cargo"))));

                filas.add(new FilaBuk(i + 1, persona, it,
                        opcional(r, cols, "instituto"), opcional(r, cols, "area"), opcional(r, cols, "nombresubarea"),
                        opcional(r, cols, "codesp"), periodo, opcional(r, cols, "tipodecontrato"),
                        opcional(r, cols, "tipotrabajador"), termino, horas,
                        cargo == null ? null : cargo.trim().toUpperCase(), opcional(r, cols, "regsic"), prob));
            }
            return new Lectura(filas, List.of());
        }
    }

    private String opcional(Row r, Map<String, Integer> cols, String clave) {
        Integer idx = cols.get(clave);
        return idx == null ? null : texto(valor(r.getCell(idx)));
    }

    static String normalizar(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static Object valor(Cell c) {
        if (c == null) return null;
        CellType t = c.getCellType();
        if (t == CellType.FORMULA) t = c.getCachedFormulaResultType();
        switch (t) {
            case NUMERIC:
                return c.getNumericCellValue();
            case STRING: {
                String s = c.getStringCellValue().trim();
                return s.isEmpty() ? null : s;
            }
            case BOOLEAN:
                return c.getBooleanCellValue();
            default:
                return null;
        }
    }

    private static String texto(Object v) {
        if (v == null) return null;
        if (v instanceof Double d) {
            if (d == Math.rint(d)) return String.valueOf(d.longValue());
            return d.toString();
        }
        return v.toString().trim();
    }

    private static Integer aEntero(Object v, String campo, List<String> prob) {
        if (v == null) { prob.add(campo + " vacio"); return null; }
        try {
            if (v instanceof Double d) return d.intValue();
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            prob.add(campo + " invalido: " + v);
            return null;
        }
    }

    private static Double aDecimal(Object v, String campo, List<String> prob) {
        if (v == null) { prob.add(campo + " vacio"); return null; }
        try {
            if (v instanceof Double d) return d;
            return Double.parseDouble(v.toString().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            prob.add(campo + " invalido: " + v);
            return null;
        }
    }

    private static LocalDate aFecha(Object v, String campo, List<String> prob) {
        if (v == null) {
            if (campo.equals("Periodo")) prob.add("Periodo vacio");
            return null;
        }
        if (v instanceof Double d) {
            return DateUtil.getJavaDate(d).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String s = v.toString().trim();
        for (DateTimeFormatter f : FORMATOS) {
            try { return LocalDate.parse(s, f); } catch (DateTimeParseException ignore) { /* prueba el siguiente */ }
        }
        prob.add(campo + " invalida: " + s);
        return null;
    }
}