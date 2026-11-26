# Escuela Escritora Marcela Paz — Experiencia de Aprendizaje 3

Aplicación Android para gestionar la disponibilidad de salas de una escuela. El flujo «En red» permite consultar salas y reservas, registrar una sala, elegir fecha y horario, consultar si la fecha es feriado nacional y cancelar reservas propias. La cuenta local identifica qué acciones muestra la interfaz. El backend recibe un identificador aleatorio para comprobar la propiedad al cancelar una reserva. Los datos personales del perfil y los documentos permanecen en el dispositivo.

Las pestañas «Solicitud local» y «Salas locales» conservan los datos y flujos Room de la versión anterior. Las reservas locales y las reservas en red son independientes.

## Arquitectura

| Componente | Responsabilidad | Persistencia |
|---|---|---|
| Android, Kotlin y Jetpack Compose | Interfaz, cuenta local, validación, consumo HTTP y consulta de feriados | Room, archivos internos y preferencias |
| Servicio de salas, Spring Boot | Catálogo de salas con nombre único y capacidad positiva | `datos/salas.json` |
| Servicio de reservas, Spring Boot | Verificación de sala por HTTP, control de cruces y cancelación por propietario | `datos/reservas.json` |
| Nager.Date | Consulta HTTPS de feriados nacionales en Chile por año | Fuente externa |

Cada microservicio corre en su propio proceso, con un perfil de Spring y un puerto diferente. El servicio de reservas consulta al de salas antes de guardar cada solicitud. Para la demostración académica, cada servicio usa un archivo JSON persistente con escritura atómica y una instancia por servicio. Se inicia en la interfaz de loopback. La autenticación de cuentas es local y el token de cancelación es una credencial de demostración. Esta configuración está diseñada para un emulador o entorno de pruebas controlado.

## Inicio rápido en Windows

Requisitos. JDK 21 o posterior para el backend, Android Studio con SDK 37 y un emulador Android con API 33 o posterior. La primera ejecución de Gradle o Maven puede necesitar internet. La consulta de feriados necesita conectividad HTTPS desde el emulador.

Usa el JAR de la entrega o genera uno desde la carpeta `backend`:

```powershell
.\mvnw.cmd clean package
```

Desde una misma carpeta de trabajo, abre dos terminales y ejecuta un comando en cada una. Si compilaste el proyecto, el JAR está en `backend/target`.

```powershell
java -jar gestion-escolar-1.0.0.jar --spring.profiles.active=salas
```

```powershell
java -jar gestion-escolar-1.0.0.jar --spring.profiles.active=reservas
```

Verifica el arranque:

```powershell
Invoke-RestMethod http://127.0.0.1:8081/api/salas
Invoke-RestMethod http://127.0.0.1:8082/api/reservas
```

Instala el APK firmado en el emulador. El APK apunta a `10.0.2.2`, que permite al emulador conectarse con el equipo que lo ejecuta.

```powershell
adb install Escuela-Marcela-Paz-EA3-release.apk
```

Si tienes otra versión con el mismo identificador y una firma distinta, usa un emulador limpio para preservar los datos de la instalación anterior.

## Roles y demostración

Las nuevas cuentas quedan como **Sin rol asignado**. Los invitados y las cuentas sin rol pueden consultar disponibilidad. Los **docentes** pueden solicitar una clase y cancelar sus propias reservas. Los **inspectores** pueden agregar o modificar nombre, capacidad y ubicación de una sala en «En red». Las solicitudes existentes guardan el nombre que tenía la sala al reservar.

Para habilitar una cuenta, entra en **Perfil → Ver credencial de rol** y entrega esa credencial solo a la persona que administra el servidor. Desde la carpeta de trabajo donde se iniciaron ambos servicios, ejecuta el script de administración del código fuente. El script pide la credencial sin mostrarla.

```powershell
& "C:\ruta\al\proyecto\backend\asignar-rol.ps1" -Rol INSPECTOR
& "C:\ruta\al\proyecto\backend\asignar-rol.ps1" -Rol DOCENTE
```

Usa el comando correspondiente a la cuenta. Para revocar el acceso, ejecuta el mismo script con `-Rol SIN_ROL`. Si iniciaste los servicios con un directorio de datos personalizado, pásalo también al script mediante `-DirectorioDatos`. Los dos servicios leen ese mismo directorio y aplican el cambio cuando la app pulsa **Actualizar**. En Android 17 permite la red local si se solicita.

Primero prueba el alta y la edición con una cuenta inspectora. Luego inicia sesión con una cuenta docente, selecciona la sala y solicita una clase. Comprueba que la app rechaza cruces en la misma fecha y que puedes cancelar tu propia reserva. Selecciona el 25 de diciembre para consultar el aviso de feriado.

Para otro destino, puedes recompilar con los parámetros Gradle `-PsalasUrl=https://...` y `-PreservasUrl=https://...`. Para un dispositivo conectado por USB puedes usar `adb reverse tcp:8081 tcp:8081` y `adb reverse tcp:8082 tcp:8082`, y compilar con URLs `http://127.0.0.1` en los puertos correspondientes.

## Contrato REST

| Método y ruta | Servicio | Resultado |
|---|---|---|
| `GET /api/salas` | 8081 | Catálogo de salas |
| `GET /api/salas/{id}` | 8081 | Datos de una sala o 404 |
| `GET /api/salas/rol` | 8081 | Devuelve el rol de la credencial enviada. |
| `POST /api/salas` | 8081 | Crea una sala con `nombre`, `capacidad` y `ubicacion`. Requiere rol inspector. |
| `PUT /api/salas/{id}` | 8081 | Modifica una sala existente. Requiere rol inspector. |
| `GET /api/reservas` | 8082 | Lista pública sin datos personales. La cabecera opcional `X-Reserva-Token` permite identificar reservas propias. |
| `POST /api/reservas` | 8082 | Crea con `salaId`, `fecha` en ISO, `inicio` y `fin` en `HH:mm`. Requiere `X-Reserva-Token` con rol docente. Retorna 201, 400 por datos inválidos, 404 por sala inexistente, 409 por cruce o 503 si salas no está disponible. |
| `DELETE /api/reservas/{id}` | 8082 | Cancela con la credencial docente de quien creó la reserva. Retorna 204 o 404. |

Android consulta `GET https://nagerholidays.com/api/v4/Holidays/CL/{año}` y filtra por la fecha seleccionada y `nationalHoliday`. La reserva puede continuar si falla la consulta de feriados. [Documentación de Nager.Date](https://github.com/nager/Nager.Date#rest-api)

## Pruebas y compilación

```powershell
.\gradlew.bat :app:testDebugUnitTest
cd backend
.\mvnw.cmd test
```

Las pruebas Android cubren validaciones de RUT y confirmación de contraseña. Las de Spring comprueban cruces, bloques contiguos y el mismo horario en otra sala o fecha.

Para generar y firmar otro APK, desde Android Studio usa **Build → Generate Signed Bundle / APK → APK → release** con tu propio almacén de claves. En línea de comandos, genera y firma con las herramientas de compilación del SDK. El APK release generado por Gradle ya está alineado.

```powershell
.\gradlew.bat :app:assembleRelease --no-configuration-cache
apksigner sign --ks escuela-release.jks --out Escuela-Marcela-Paz-EA3-release.apk app\build\outputs\apk\release\app-release-unsigned.apk
apksigner verify --verbose --print-certs Escuela-Marcela-Paz-EA3-release.apk
```

El modo release desactiva la minificación porque la optimización parcial heredada provocaba un error al iniciar AndroidX en este proyecto. El APK entregado usa una firma de demostración. Guarda tu propia clave si vas a distribuir actualizaciones. Los APK con firmas distintas se tratan como instalaciones incompatibles para una actualización.

## Alcance frente al EFT

El proyecto aborda una necesidad del caso Escuela Escritora Marcela Paz. Permite registrar y consultar disponibilidad y evitar cruces de reservas de salas. La correspondencia con los parámetros específicos del EFT queda pendiente de revisión cuando se facilite esa pauta.




