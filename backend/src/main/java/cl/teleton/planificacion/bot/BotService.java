package cl.teleton.planificacion.bot;

import org.springframework.web.multipart.MultipartFile;

/** Punto de integracion con Blue Prism: quien recibe el reporte y lo lleva a la base de datos. */
public interface BotService {
    Object procesar(MultipartFile archivo) throws Exception;

    String modo();
}
