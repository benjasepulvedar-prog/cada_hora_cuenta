package cl.teleton.planificacion.bot;

import cl.teleton.planificacion.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Modo real: deja el archivo en la carpeta de entrada y lanza el proceso de Blue Prism con AutomateC.exe.
 * El bot escribe en la BD (contratos, cambios_detectados, errores_carga, log_ejecucion) y el backend
 * recalcula los cambios PENDIENTES con el scheduler (RecalculoScheduler).
 */
@Service
@ConditionalOnProperty(prefix = "app.bot", name = "modo", havingValue = "BLUEPRISM")
public class BluePrismBotService implements BotService {

    private final AppProperties props;

    public BluePrismBotService(AppProperties props) { this.props = props; }

    @Override
    public Object procesar(MultipartFile archivo) throws Exception {
        Path dir = Path.of(props.carpetaEntrada());
        Files.createDirectories(dir);
        Path destino = dir.resolve(UUID.randomUUID() + ".xlsx").toAbsolutePath();
        archivo.transferTo(destino);

        String startp = "<inputs><input name='Ruta Archivo' type='text' value='" + escaparXml(destino.toString()) + "'/></inputs>";
        ProcessBuilder pb = new ProcessBuilder(props.automateExe(), "/run", props.proceso(), "/startp", startp, "/sso");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int codigo = p.waitFor();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modo", modo());
        out.put("archivo", destino.getFileName().toString());
        out.put("estado", codigo == 0 ? "ENVIADO_A_BLUE_PRISM" : "ERROR_AL_LANZAR_BOT");
        out.put("codigoRetorno", codigo);
        out.put("salidaAutomateC", salida);
        out.put("siguientePaso", "Consulta GET /api/carga/estado para ver el resultado que deja el bot");
        return out;
    }

    @Override
    public String modo() { return "BLUEPRISM"; }

    private static String escaparXml(String s) {
        return s.replace("&", "&amp;").replace("'", "&apos;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
