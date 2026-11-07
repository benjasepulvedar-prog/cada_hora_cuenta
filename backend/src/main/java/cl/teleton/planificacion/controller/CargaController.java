package cl.teleton.planificacion.controller;

import cl.teleton.planificacion.bot.BotService;
import cl.teleton.planificacion.repository.CargaRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Entrada HTTP para cargar el Excel y consultar su historial de procesamiento. */
@RestController
@RequestMapping("/api/carga")
public class CargaController {

    private final BotService bot;
    private final CargaRepository repository;

    public CargaController(BotService bot, CargaRepository repository) {
        this.bot = bot;
        this.repository = repository;
    }

    @PostMapping("/buk")
    public Object subirBuk(@RequestParam("archivo") MultipartFile archivo) throws Exception {
        String nombre = archivo.getOriginalFilename();
        if (archivo.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo esta vacio");
        if (nombre == null || !nombre.toLowerCase().endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se aceptan archivos .xlsx");
        }
        return bot.procesar(archivo);
    }

    @GetMapping("/estado")
    public List<Map<String, Object>> estado() { return repository.ejecucionesRecientes(); }

    @GetMapping("/errores")
    public List<Map<String, Object>> errores(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        return repository.erroresRecientes(limit);
    }
}