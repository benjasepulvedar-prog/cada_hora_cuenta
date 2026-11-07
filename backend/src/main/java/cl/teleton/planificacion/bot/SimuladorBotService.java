package cl.teleton.planificacion.bot;

import cl.teleton.planificacion.config.AppProperties;
import cl.teleton.planificacion.model.ResultadoCarga;
import cl.teleton.planificacion.service.CalculoService;
import cl.teleton.planificacion.service.CargaService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * SOLO PARA DESARROLLO LOCAL: reemplaza al bot. Hace dentro del backend lo mismo que hara el proceso de
 * Blue Prism (leer, validar, comparar, cargar). En la demo real este paso lo ejecuta Blue Prism.
 */
@Service
@ConditionalOnProperty(prefix = "app.bot", name = "modo", havingValue = "SIMULADO", matchIfMissing = true)
public class SimuladorBotService implements BotService {

    private final CargaService carga;
    private final CalculoService calculo;
    private final AppProperties props;

    public SimuladorBotService(CargaService carga, CalculoService calculo, AppProperties props) {
        this.carga = carga;
        this.calculo = calculo;
        this.props = props;
    }

    @Override
    public Object procesar(MultipartFile archivo) throws Exception {
        Path dir = Path.of(props.carpetaEntrada());
        Files.createDirectories(dir);
        Path destino = dir.resolve(UUID.randomUUID() + ".xlsx");   // nombre generado: nunca se usa el del cliente
        archivo.transferTo(destino);

        ResultadoCarga r = carga.procesar(destino, archivo.getOriginalFilename());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modo", modo());
        out.put("carga", r);
        if (r.estado().startsWith("OK")) {
            out.put("calculo", r.cargaInicial() ? calculo.recalcularTodo() : calculo.aplicarPendientes());
        }
        return out;
    }

    @Override
    public String modo() { return "SIMULADO"; }
}
