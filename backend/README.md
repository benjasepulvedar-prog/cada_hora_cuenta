# Planificacion asistencial - Backend (Botathon 2026 "Cada hora cuenta")

Backend Spring Boot 3 / Java 17+ para correr **en local** sin Blue Prism ni SQL Server.
Usa H2 persistida en disco por defecto y un **simulador del bot** que hace lo mismo que hara el proceso de Blue Prism
(leer el Excel de BUK, validar, comparar con la version anterior y cargar solo lo que cambio).

> Estado: el codigo pasa una revision de sintaxis y la formula de calculo fue probada contra una fila
> del Excel de referencia, pero **el proyecto completo no se compilo ni se ejecuto** en el entorno donde se
> genero (sin Maven ni acceso a internet). Si Maven falla al compilar, copia el error y se corrige.

## Requisitos
- JDK 17 o superior (`java -version`)
- Maven 3.9+ (`mvn -v`) o un IDE (IntelliJ / VS Code) que importe el `pom.xml`

## Ejecutar
```bash
mvn spring-boot:run        # arranca en http://localhost:8080
mvn test                   # prueba de la formula (caso Fisiatra 40 h del Excel)
```
- Swagger UI (probar endpoints desde el navegador): http://localhost:8080/swagger-ui.html
- Consola H2: http://localhost:8080/h2-console (JDBC `jdbc:h2:file:./data/planificacion`, user `sa`, sin clave)
- La URL y credenciales se pueden reemplazar con `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`.

El frontend está en `../frontend/`. Inicia el backend en el puerto 8080 y abre `login.html`; el dashboard consulta
`http://localhost:8080/api` y permite subir reportes `.xlsx` desde la misma interfaz.

Al arrancar se cargan los parametros del Excel (`data.sql`): 92 cargos con su distribucion por tramo,
factores de atencion grupal, semanas habiles por mes, meta de gestion 90% y catalogo de asignaciones.

## Flujo de demo (carpeta `samples/`)
1. `POST /api/carga/buk` con `1_Base_contratos_inicial.xlsx` -> carga inicial y calculo completo.
2. `GET /api/resumen?agruparPor=it` y `GET /api/resumen/nacional` -> consolidado y FOJA.
3. `POST /api/carga/buk` con `2_Base_contratos_con_cambios.xlsx`. Contiene, a proposito:
   - JORNADA: PersonaId 71146 baja de 30 a 22 h desde mayo
   - CARGO: PersonaId 123727 pasa a ENFERMERA COORDINADORA desde junio
   - ALTA: PersonaId 999001 desde junio
   - BAJA: PersonaId 71132 ya no aparece en el reporte
   - 2 filas con error: un cargo inexistente y una fila con 99 horas semanales (se rechazan, no se cargan)
4. `GET /api/carga/estado`, `/api/carga/errores`, `/api/cambios` -> lo que detecto el bot.
5. `GET /api/resumen/profesional/71146` -> solo ese profesional se recalculo, desde mayo.
6. Cambiar una regla: `PUT /api/parametros/cargos/MEDICO FISIATRA?recalcular=true` y volver a mirar el resumen.

Todas las llamadas estan listas en `requests.http` (VS Code REST Client / IntelliJ) y se pueden hacer con curl:
```bash
curl -F "archivo=@samples/1_Base_contratos_inicial.xlsx" http://localhost:8080/api/carga/buk
curl "http://localhost:8080/api/resumen?agruparPor=it"
```

## Endpoints
| Metodo | Ruta | Para que sirve |
|---|---|---|
| GET | /api/salud | Estado, modo del bot y conteos |
| POST | /api/carga/buk | Sube el reporte .xlsx (campo `archivo`); valida, guarda en la BD y actualiza el cálculo |
| GET | /api/carga/estado | Log de las ultimas ejecuciones del bot |
| GET | /api/carga/errores | Errores de validacion del reporte |
| GET | /api/cambios?estado=&tipo=&personaId= | Altas, bajas y cambios detectados |
| POST | /api/calculo/pendientes | Aplica cambios pendientes desde su fecha de vigencia |
| POST | /api/calculo/recalcular | Recalcula todo con los parametros vigentes |
| GET | /api/resumen?agruparPor=&it=&mes=&area=&cargo=&codEsp=&personaId= | Dashboard. agruparPor: it, instituto, area, cargo, codEsp, mes, profesional |
| GET | /api/resumen/nacional | Totales nacionales y por mes |
| GET | /api/resumen/profesional/{personaId} | Detalle mensual de un profesional |
| GET/PUT | /api/parametros/cargos[/{cargo}] | % directo / indirecto / NAS por tramo y minutos estandar |
| GET/PUT | /api/parametros/generales[/{clave}] | Meta de gestion, minutos indirecta, maximo de asignaciones |
| GET/PUT | /api/parametros/calendario[/{mes}] y /instituto/{it}/{mes} | Semanas habiles nacionales y por instituto |
| GET | /api/parametros/asignaciones | Catalogo de asignaciones especiales |
| GET/POST | /api/asignaciones | Listar / solicitar (max. 2 si el profesional trabaja mas de 30 h) |
| POST/DELETE | /api/asignaciones/{id}/aprobar , /{id} | Autorizar / eliminar |
| GET | /api/institutos | IT e institutos presentes en el reporte |

Los PUT de parametros aceptan `?recalcular=true` para ver el efecto de inmediato.

## Como se calcula (por profesional y mes)
1. Tramo segun el promedio de horas semanales del profesional: >=40, 22-40, <22.
2. Horas NAS = horas del mes x %NAS del cargo/tramo; horas disponibles = horas - NAS - asignaciones aprobadas.
3. Se reparten entre directa e indirecta segun la proporcion %Dir : %Ind del cargo.
4. Atenciones por semana = horas x 60 / minutos estandar (directa) y x 60 / 30 (indirecta), con redondeo hacia abajo.
   Si el area tiene atencion grupal, la directa se multiplica por (1 - %grupal + %grupal x pacientes por grupo).
5. Plan del mes = atenciones por semana x semanas habiles del mes. Meta de gestion = 90% del plan. FOJA = horas disponibles directas / horas contratadas.

Contraste con la hoja `Dotacion_Asistencial` (471 profesionales): la atencion semanal coincide exactamente en ~84% de los casos.
El resto difiere por detalles que este MVP aun no modela: licencias, vacaciones y permisos (semanas descontadas),
excepciones manuales del Excel, "Cambio % atencion grupal" aprobado y horas de colacion/pausa saludable.

## Pasar de simulador a Blue Prism
En `application.yml`:
```yaml
app.bot.modo: BLUEPRISM
```
- El backend deja el archivo en `app.bot.carpeta-entrada` y lanza `AutomateC.exe /run "<proceso>" /startp ...`
  (ver `BluePrismBotService`).
- El bot debe escribir en las tablas `contratos`, `cambios_detectados`, `errores_carga` y `log_ejecucion`
  (ver `schema.sql`). `RecalculoScheduler` aplica los cambios PENDIENTES cada 15 s.
- La H2 local está pensada para desarrollo y no para acceso concurrente desde un proceso externo: configura SQL Server / PostgreSQL para Blue Prism.
  Las sentencias `MERGE ... KEY(...)` son sintaxis de H2 y hay que adaptarlas a ese motor.
- La logica de `CargaService` (validaciones, llave `PersonaId|IT|Periodo`, tipos de cambio, BAJA = ya no aparece
  en el reporte, rige desde el ultimo mes del archivo) es la especificacion de lo que debe hacer el proceso de Blue Prism.

## Fuera de alcance de este MVP
Autenticacion y roles (hay que agregarlos antes de usar datos reales: el reporte contiene datos personales; este
proyecto no guarda la fecha de nacimiento), comparacion contra SIC, aprobaciones con historial de versiones,
licencias/vacaciones/permisos, feriados regionales precargados (se pueden cargar con el endpoint de calendario) y el frontend.

## Estructura
```
src/main/java/cl/teleton/planificacion/
  controller/  Endpoints REST de carga, dashboard, calculo, parametros y asignaciones
  model/       Modelos de dominio y filtros/resultado de carga
  repository/  Persistencia y consultas de contratos, cargas y dashboard
  service/     Lectura/validacion de Excel, calculos y consultas de negocio
  bot/         Integracion de simulacion local o Blue Prism
  calculo/     Formula pura de calculo y scheduler
  config/      Configuracion de la aplicacion
src/main/resources/ application.yml, schema.sql, data.sql (parametros del Excel)
samples/     reportes de ejemplo (inicial y con cambios)
```

La prueba de integración `CargaDashboardIntegrationTest` genera un `.xlsx`, lo envía por HTTP,
comprueba la persistencia y consulta el resumen utilizado por el dashboard. Ejecutar con `mvn test`.
