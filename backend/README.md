# Planificacion asistencial - Backend (Botathon 2026 "Cada hora cuenta")

Backend Spring Boot 3 / Java 17+ configurado para utilizar Blue Prism por defecto. Por defecto utiliza H2
persistida en disco; con el perfil `neon` utiliza PostgreSQL/Neon. El modo `SIMULADO` se reserva para pruebas o
ejecuciones explícitas sin Blue Prism.

## Requisitos
- JDK 17 o superior (`java -version`)
- Maven 3.9+ (`mvn -v`) o un IDE (IntelliJ / VS Code) que importe el `pom.xml`

## Ejecutar
```bash
mvn spring-boot:run        # arranca en http://localhost:8080
mvn test                   # pruebas unitarias y de integración
```
- Swagger UI (probar endpoints desde el navegador): http://localhost:8080/swagger-ui.html
- Consola H2: http://localhost:8080/h2-console (JDBC `jdbc:h2:file:./data/planificacion`, user `sa`, sin clave)
- La URL y credenciales se pueden reemplazar con `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`.

### Base de datos Neon desde PowerShell

El perfil `neon` selecciona PostgreSQL, crea las tablas que falten y carga los parámetros de referencia. No guarda
la clave en `application.yml`, en Git ni dentro de la URL. En PowerShell, reemplaza el host por el endpoint de Neon
y ejecuta:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'neon'
$env:DB_URL = 'jdbc:postgresql://<endpoint-neon>/neondb?sslmode=require&channelBinding=require'
$credential = Get-Credential -UserName 'neondb_owner' -Message 'Credenciales de Neon'
$env:DB_USERNAME = $credential.UserName
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
& "$HOME\.maven\maven-3.10.0\bin\mvn.cmd" spring-boot:run
```

`Get-Credential` solicita la clave de forma oculta. Estas variables solo aplican a esa ventana de PowerShell; al cerrar
la ventana desaparecen. La consola H2 se desactiva en el perfil Neon.

El esquema y los parámetros iniciales se aplican al arrancar desde `schema-postgresql.sql` y `data-postgresql.sql`.
Esto crea las tablas faltantes; no importa automáticamente el reporte de dotación. Sube el archivo desde el dashboard
después de iniciar. Los datos iniciales usan `ON CONFLICT DO NOTHING`, así que no sustituyen parámetros ya editados.
`CREATE TABLE IF NOT EXISTS` no migra tablas antiguas si cambia el esquema; para futuras versiones debe añadirse
Flyway u otra estrategia de migraciones.

El frontend está en `../frontend/`. Inicia el backend en el puerto 8080 y abre `login.html`; el dashboard consulta
`http://localhost:8080/api`, permite subir reportes `.xlsx`, consultar el resumen/detalle y descargar el CSV generado
por el backend desde la base de datos. El CSV contiene IDs, no nombres: la planilla de contratos no trae nombres.
Las atenciones realizadas/SIC no se muestran como datos reales porque no hay una API ni fuente SIC integrada.

Para pruebas locales sin una instalación Blue Prism, configura explícitamente `$env:APP_BOT_MODO='SIMULADO'`
en PowerShell antes de iniciar. No se aplica como fallback automático.

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
| GET | /api/resumen/detalle?it=&mes=&area=&personaId= | Filas planificadas para la vista de profesionales |
| GET | /api/resumen/exportar.csv?it=&mes=&area=&personaId= | CSV filtrado generado desde la BD |
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

## Blue Prism
El modo predeterminado es `BLUEPRISM`. Configura `BLUEPRISM_AUTOMATE_EXE`, `BLUEPRISM_PROCESO` y
`BLUEPRISM_INPUT_DIR` si las rutas difieren de los valores por defecto. Si no encuentra `AutomateC.exe`, la carga
responde `ERROR_BLUE_PRISM_NO_INSTALADO`; no procesa el Excel con Java como alternativa silenciosa.

El backend entrega el archivo al proceso externo con:
```yaml
app.bot.modo: BLUEPRISM
```
- El backend deja el `.xlsx` en `app.bot.carpeta-entrada` y lanza `AutomateC.exe` (ver `BluePrismBotService`).
- El proceso Blue Prism debe existir, estar instalado y configurado para conectarse a Neon, leer el Excel y escribir
  las tablas `contratos`, `cambios_detectados`, `errores_carga` y `log_ejecucion`. Ese proceso externo no forma parte
  de este repositorio; aquí solo se invoca y se consulta su resultado.
- El perfil Neon usa upserts PostgreSQL; el perfil H2 conserva las sentencias propias de H2.
- La logica de `CargaService` (validaciones, llave `PersonaId|IT|Periodo`, tipos de cambio, BAJA = ya no aparece
  en el reporte, rige desde el ultimo mes del archivo) es la especificacion de lo que debe hacer el proceso de Blue Prism.

## Pendiente antes de producción
Autenticación y autorización reales (el login actual es solo del frontend y los endpoints no protegen por rol),
comparación con SIC, nombres de profesionales, aprobaciones con historial de versiones, licencias/vacaciones/permisos
y feriados regionales precargados. La fecha de nacimiento del Excel se ignora y no se almacena.

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
src/main/resources/ application*.yml, schema*.sql, data*.sql (parametros del Excel)
samples/     reportes de ejemplo (inicial y con cambios)
```

La prueba de integración `CargaDashboardIntegrationTest` genera un `.xlsx`, lo envía por HTTP,
comprueba la persistencia y consulta el resumen utilizado por el dashboard. Ejecutar con `mvn test`.
