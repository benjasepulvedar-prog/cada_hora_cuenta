package cl.teleton.planificacion.bot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cl.teleton.planificacion.config.AppProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class BluePrismBotServiceTest {

    private static final String AUTOMATE_EXE = "C:\\Blue Prism\\AutomateC.exe";
    private static final String PROCESO = "Procesar Reporte BUK";
    private static final byte[] CONTENIDO = "contenido xlsx de prueba".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    @Test
    void guardaArchivoYLanzaAutomateConParametrosEscapados() throws Exception {
        Path carpetaEntrada = Files.createDirectory(tempDir.resolve("entrada & ' datos"));
        AppProperties props = new AppProperties("BLUEPRISM", carpetaEntrada.toString(), AUTOMATE_EXE, PROCESO);
        AtomicReference<List<String>> comandoRecibido = new AtomicReference<>();
        BluePrismBotService service = new BluePrismBotService(props, command -> {
            comandoRecibido.set(command);
            return new FakeProcess(" ejecución correcta ", 0);
        });

        var resultado = service.procesar(new MockMultipartFile("archivo", "reporte.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", CONTENIDO));

        @SuppressWarnings("unchecked")
        var respuesta = (java.util.Map<String, Object>) resultado;
        Path archivoGuardado = carpetaEntrada.resolve((String) respuesta.get("archivo"));
        assertArrayEquals(CONTENIDO, Files.readAllBytes(archivoGuardado));
        assertEquals("BLUEPRISM", respuesta.get("modo"));
        assertEquals("ENVIADO_A_BLUE_PRISM", respuesta.get("estado"));
        assertEquals(0, respuesta.get("codigoRetorno"));
        assertEquals("ejecución correcta", respuesta.get("salidaAutomateC"));
        assertEquals(List.of(AUTOMATE_EXE, "/run", PROCESO, "/startp",
                "<inputs><input name='Ruta Archivo' type='text' value='"
                        + archivoGuardado.toAbsolutePath().toString()
                                .replace("&", "&amp;").replace("'", "&apos;")
                                .replace("<", "&lt;").replace(">", "&gt;")
                        + "'/></inputs>", "/sso"), comandoRecibido.get());
    }

    @Test
    void informaCuandoAutomateRetornaError() throws Exception {
        AppProperties props = new AppProperties("BLUEPRISM", tempDir.toString(), AUTOMATE_EXE, PROCESO);
        BluePrismBotService service = new BluePrismBotService(props,
                command -> new FakeProcess("falló", 7));

        var resultado = service.procesar(new MockMultipartFile("archivo", "reporte.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", CONTENIDO));

        @SuppressWarnings("unchecked")
        var respuesta = (java.util.Map<String, Object>) resultado;
        assertEquals("ERROR_AL_LANZAR_BOT", respuesta.get("estado"));
        assertEquals(7, respuesta.get("codigoRetorno"));
        assertTrue(Files.exists(tempDir.resolve((String) respuesta.get("archivo"))));
    }

    private static final class FakeProcess extends Process {
        private final ByteArrayInputStream output;
        private final int exitCode;

        private FakeProcess(String output, int exitCode) {
            this.output = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
            this.exitCode = exitCode;
        }

        @Override public java.io.OutputStream getOutputStream() { return new java.io.ByteArrayOutputStream(); }
        @Override public java.io.InputStream getInputStream() { return output; }
        @Override public java.io.InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { return exitCode; }
        @Override public int exitValue() { return exitCode; }
        @Override public void destroy() {}
    }
}
