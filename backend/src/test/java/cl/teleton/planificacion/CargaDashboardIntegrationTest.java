package cl.teleton.planificacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import cl.teleton.planificacion.bot.BluePrismBotService;
import cl.teleton.planificacion.config.AppProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:planificacion-test;DB_CLOSE_DELAY=-1",
        "app.bot.modo=SIMULADO",
        "app.bot.carpeta-entrada=target/test-entrada"
})
@AutoConfigureMockMvc
class CargaDashboardIntegrationTest {

    private static final int PERSONA_ID = 987654;
    private static final String ARCHIVO = "test-upload.xlsx";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void limpiarDatosDePrueba() {
        jdbc.update("DELETE FROM resultado_planificacion WHERE persona_id = ?", PERSONA_ID);
        jdbc.update("DELETE FROM cambios_detectados WHERE persona_id = ?", PERSONA_ID);
        jdbc.update("DELETE FROM contratos WHERE persona_id = ?", PERSONA_ID);
        jdbc.update("DELETE FROM errores_carga WHERE archivo = ?", ARCHIVO);
        jdbc.update("DELETE FROM log_ejecucion WHERE archivo = ?", ARCHIVO);
    }

    @Test
    void cargaExcelPersisteYAlimentaDashboard() throws Exception {
        MockMultipartFile excel = new MockMultipartFile("archivo", ARCHIVO,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", crearExcel());

        mvc.perform(multipart("/api/carga/buk").file(excel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carga.estado").value("OK_CON_ERRORES"))
                .andExpect(jsonPath("$.carga.filasValidas").value(1))
                .andExpect(jsonPath("$.carga.totalErrores").value(1))
                .andExpect(jsonPath("$.calculo.filasCalculadas").value(1));

        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM contratos WHERE persona_id = ?", Integer.class, PERSONA_ID));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM resultado_planificacion WHERE persona_id = ?", Integer.class, PERSONA_ID));

        mvc.perform(get("/api/resumen").param("agruparPor", "it").param("mes", "1").param("it", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clave").value(1))
                .andExpect(jsonPath("$[0].profesionales").value(1));

        mvc.perform(get("/api/resumen/nacional").param("mes", "1").param("it", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.profesionales").value(1))
                .andExpect(jsonPath("$.totales.horasSemanales").value(40));

        mvc.perform(get("/api/institutos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ciudad == 'Puerto Montt')]").isNotEmpty());

        mvc.perform(get("/api/resumen/detalle").param("personaId", String.valueOf(PERSONA_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ciudad").value("Puerto Montt"))
                .andExpect(jsonPath("$[0].subArea").value("Servicio Social"));

        mvc.perform(get("/api/resumen/exportar.csv").param("personaId", String.valueOf(PERSONA_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Puerto Montt")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(String.valueOf(PERSONA_ID))));

        mvc.perform(get("/api/carga/errores").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].archivo").value(ARCHIVO));
    }

    @Test
    void rechazaArchivoQueNoEsXlsx() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "datos.csv", "text/csv", "a,b".getBytes());
        mvc.perform(multipart("/api/carga/buk").file(archivo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dashboardRechazaAgrupacionNoPermitida() throws Exception {
        mvc.perform(get("/api/resumen").param("agruparPor", "columna-inyectada"))
                .andExpect(status().isBadRequest());
    }

        @Test
        void bluePrismInformaCuandoFaltaAutomateC() throws Exception {
                BluePrismBotService bluePrism = new BluePrismBotService(new AppProperties(
                                "BLUEPRISM", "target/test-entrada", "target/AutomateC-no-instalado.exe", "Procesar Reporte BUK"));
                MockMultipartFile archivo = new MockMultipartFile("archivo", "reporte.xlsx", "application/octet-stream", new byte[]{1});

                @SuppressWarnings("unchecked")
                Map<String, Object> respuesta = (Map<String, Object>) bluePrism.procesar(archivo);

                org.junit.jupiter.api.Assertions.assertEquals("ERROR_BLUE_PRISM_NO_INSTALADO", respuesta.get("estado"));
                org.junit.jupiter.api.Assertions.assertTrue(respuesta.get("mensaje").toString().contains("BLUEPRISM_AUTOMATE_EXE"));
        }

    private byte[] crearExcel() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dotacion_2026");
            String[] encabezados = {"IT", "Instituto", "PersonaId", "Área", "Nombre Sub-área", "Centro de Costo",
                    "Fecha de Nacimiento", "Periodo", "Tipo de Contrato", "Tipo_Trab_161225", "Fecha Ingreso Compañía",
                    "Fecha Término", "Horas Semanales", "Cargo", "Tipo_Trabajador", "Días de la Jornada",
                    "Reg_SIC", "Cod_Esp", "Ndias"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < encabezados.length; i++) header.createCell(i).setCellValue(encabezados[i]);

            Row valida = sheet.createRow(1);
            valida.createCell(0).setCellValue(1);
            valida.createCell(1).setCellValue("PMo");
            valida.createCell(2).setCellValue(PERSONA_ID);
            valida.createCell(3).setCellValue("Puerto Montt");
            valida.createCell(4).setCellValue("Servicio Social");
            valida.createCell(7).setCellValue("2026-01-01");
            valida.createCell(8).setCellValue("Indefinido");
            valida.createCell(12).setCellValue(40);
            valida.createCell(13).setCellValue("MEDICO FISIATRA");
            valida.createCell(17).setCellValue("AS");

            Row invalida = sheet.createRow(2);
            invalida.createCell(0).setCellValue(1);
            invalida.createCell(1).setCellValue("PMo");
            invalida.createCell(2).setCellValue(PERSONA_ID);
            invalida.createCell(7).setCellValue("2026-02-01");
            invalida.createCell(12).setCellValue(99);
            invalida.createCell(13).setCellValue("MEDICO FISIATRA");
            invalida.createCell(17).setCellValue("AS");

            workbook.write(output);
            return output.toByteArray();
        }
    }
}