package cl.teleton.planificacion.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Convierte claves de columnas (PERSONA_ID) a camelCase (personaId) para devolver JSON limpio. */
public final class Json {
    private Json() {}

    public static List<Map<String, Object>> camel(List<Map<String, Object>> filas) {
        List<Map<String, Object>> out = new ArrayList<>(filas.size());
        for (Map<String, Object> f : filas) out.add(camel(f));
        return out;
    }

    public static Map<String, Object> camel(Map<String, Object> fila) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : fila.entrySet()) out.put(aCamel(e.getKey()), e.getValue());
        return out;
    }

    static String aCamel(String s) {
        String[] p = s.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder(p[0]);
        for (int i = 1; i < p.length; i++) {
            if (p[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(p[i].charAt(0))).append(p[i].substring(1));
        }
        return sb.toString();
    }
}
