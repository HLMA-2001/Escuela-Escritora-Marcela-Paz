# Escuela Escritora Marcela Paz — Experiencia de Aprendizaje 3

Aplicación Android para gestionar la disponibilidad de salas de una escuela. En «Reservas» se consultan las salas disponibles para un horario y se solicitan o cancelan reservas. El backend recibe una credencial aleatoria para verificar el rol y la propiedad de la reserva. Los datos personales del perfil y los documentos permanecen en el dispositivo.

La pestaña «Reservas» reúne los modos «En red» y «En este dispositivo». Cada modo verifica la disponibilidad en su propio catálogo y conserva las reservas existentes.

El catálogo local predefinido incluye 12 aulas numeradas para la app, dos salas de computación, un laboratorio de ciencias y una biblioteca. Se tomó como referencia el establecimiento de San Bernardo (RBD 10571). La capacidad de cada sala figura como pendiente de confirmación. [Proyecto Educativo Institucional, pág. 7](https://wwwfs.mineduc.cl/Archivos/infoescuelas/documentos/10571/ProyectoEducativo10571.pdf)

## Arquitectura

| Componente | Responsabilidad | Persistencia |
|---|---|---|
| Android, Kotlin y Jetpack Compose | Interfaz, cuenta local, validación y consumo HTTP | Room, archivos internos y preferencias |
| Servicio de salas, Spring Boot | Catálogo de salas con nombre único y capacidad positiva | `datos/salas.json` |
| Servicio de reservas, Spring Boot | Verificación de sala por HTTP, control de cruces y cancelación por propietario | `datos/reservas.json` |

Cada microservicio corre en su propio proceso, con un perfil de Spring y un puerto diferente. El servicio de reservas consulta al de salas antes de guardar cada solicitud. Para la demostración académica, cada servicio usa un archivo JSON persistente con escritura atómica y una instancia por servicio. Se inicia en la interfaz de loopback. La autenticación de cuentas es local y el token de cancelación es una credencial de demostración. Esta configuración está diseñada para un emulador o entorno de pruebas controlado.

## Inicio rápido en Windows

Abre la carpeta raíz en Android Studio y sincroniza Gradle para cargar los módulos `:app` y `:backend`. El proyecto usa JDK 25 para ejecutar Gradle y JDK 21 para compilar el backend con el toolchain de Gradle. La app requiere SDK 37 y un emulador con API 33 o posterior. La primera ejecución de Gradle puede necesitar internet.

Abre dos terminales en la raíz del proyecto y ejecuta un comando en cada una. Ambos servicios usarán la carpeta `datos` de la raíz.

```powershell
.\gradlew.bat :backend:bootRun --args="--spring.profiles.active=salas"
```

```powershell
.\gradlew.bat :backend:bootRun --args="--spring.profiles.active=reservas"
```

También puedes generar el JAR ejecutable con `.\gradlew.bat :backend:bootJar`. Queda en `backend/build/libs/gestion-escolar-1.0.0.jar` y acepta los mismos argumentos de perfil con `java -jar`.

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

Las cuentas nuevas reciben el rol **Docente** al iniciar sesión y conectar con el servicio de salas. Los docentes pueden solicitar una sala y cancelar sus propias solicitudes. Los inspectores pueden aprobar, editar o cancelar solicitudes en ambos modos de reserva. Una solicitud pendiente no bloquea la sala. Al aprobarla se verifican los cruces otra vez. La sala figura ocupada durante el bloque aprobado.

En **Cuentas**, el Inspector puede actualizar el nombre y correo de las cuentas registradas en el dispositivo. Puede asignar o revocar roles desde una cuenta local o pegar la credencial de una cuenta registrada en otro dispositivo. El servidor impide que el Inspector revoque su propio rol desde la app.

Para habilitar una cuenta, entra en **Perfil → Ver credencial de rol** y entrega esa credencial solo a la persona que administra el servidor. Desde la carpeta de trabajo donde se iniciaron ambos servicios, ejecuta el script de administración del código fuente. El script pide la credencial sin mostrarla.

```powershell
& "C:\ruta\al\proyecto\backend\asignar-rol.ps1" -Rol INSPECTOR
& "C:\ruta\al\proyecto\backend\asignar-rol.ps1" -Rol DOCENTE
```

Usa el comando correspondiente a la cuenta. Para revocar el acceso, ejecuta el mismo script con `-Rol SIN_ROL`. Si iniciaste los servicios con un directorio de datos personalizado, pásalo también al script mediante `-DirectorioDatos`. Los dos servicios leen ese mismo directorio y aplican el cambio cuando la app pulsa **Actualizar**. En Android 17 permite la red local si se solicita.

El rol Inspector queda guardado sin vencimiento en `datos/roles.json`. Cerrar sesión o reiniciar el servidor conserva el rol mientras la cuenta local y el archivo de roles se mantengan.

Inicia sesión con una cuenta docente y solicita una sala disponible. Luego inicia sesión con una cuenta inspectora usando el mismo botón **Entrar** y pulsa **Aprobar reserva**. El servicio de salas debe estar activo para verificar el rol.

Para otro destino, puedes recompilar con los parámetros Gradle `-PsalasUrl=https://...` y `-PreservasUrl=https://...`. Para un dispositivo conectado por USB puedes usar `adb reverse tcp:8081 tcp:8081` y `adb reverse tcp:8082 tcp:8082`, y compilar con URLs `http://127.0.0.1` en los puertos correspondientes.

## Contrato REST

| Método y ruta | Servicio | Resultado |
|---|---|---|
| `GET /api/salas` | 8081 | Catálogo de salas |
| `GET /api/salas/{id}` | 8081 | Datos de una sala o 404 |
| `GET /api/salas/rol` | 8081 | Devuelve el rol de la credencial enviada. |
| `PUT /api/salas/rol` | 8081 | Asigna `rol` a una `credencial`. Requiere rol inspector. |
| `POST /api/salas/rol/docente` | 8081 | Da de alta una credencial nueva como Docente. Conserva los roles existentes, incluidas las revocaciones. |
| `POST /api/salas` | 8081 | Crea una sala con `nombre`, `capacidad` y `ubicacion`. Requiere rol inspector. |
| `PUT /api/salas/{id}` | 8081 | Modifica una sala existente. Requiere rol inspector. |
| `GET /api/reservas` | 8082 | Lista pública sin datos personales. La cabecera opcional `X-Reserva-Token` permite identificar reservas propias. |
| `POST /api/reservas` | 8082 | Crea con `salaId`, `fecha` en ISO, `inicio` y `fin` en `HH:mm`. Requiere `X-Reserva-Token` con rol docente. Retorna 201, 400 por datos inválidos, 404 por sala inexistente, 409 por cruce o 503 si salas no está disponible. |
| `PUT /api/reservas/{id}` | 8082 | Cambia sala, fecha u horario y conserva al propietario. Requiere rol inspector. |
| `PUT /api/reservas/{id}/aprobar` | 8082 | Confirma una solicitud pendiente si el bloque está libre. Requiere rol inspector. |
| `PUT /api/reservas/{id}/rechazar` | 8082 | Rechaza una solicitud pendiente y conserva el resultado para su propietario. Requiere rol inspector. |
| `DELETE /api/reservas/{id}` | 8082 | Inspector puede cancelar cualquier reserva. Docente puede cancelar la propia. Retorna 204 o 404. |

## Pruebas y compilación

```powershell
.\gradlew.bat :app:testDebugUnitTest :backend:test
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




