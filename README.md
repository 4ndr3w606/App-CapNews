# CapNews

CapNews es una aplicación nativa para Android que reúne titulares en español, noticias cercanas y el clima del lugar que elijas. Puedes programar un resumen diario, guardar favoritos y descargar artículos para leerlos sin conexión.

## Funcionalidades

| Función | Qué puedes hacer |
| --- | --- |
| Registro y acceso | Crear una cuenta con nombre, correo y contraseña. El formulario pide confirmar el correo y la contraseña. Debes desplazarte hasta el final de los términos para habilitar **Aceptar**. Tras registrarte, vuelves a la pantalla de inicio de sesión. |
| Inicio y titulares | Ver tu nombre y correo, abrir la última categoría seleccionada y buscar noticias en español. La búsqueda de GNews abarca el título, la descripción y el cuerpo del artículo. |
| Cerca de ti | Consultar noticias de la ciudad elegida o de la zona detectada mediante ubicación aproximada. Cuando hay pocos resultados y se conoce la región, la app permite ampliar la búsqueda a esa región. |
| Titulares diarios | Programa una notificación con un titular por cada categoría seleccionada que devuelva resultados. Cada cuenta guarda su propio horario y selección. |
| Clima | Consultar la temperatura actual y el estado del tiempo con Open-Meteo. Puedes elegir una ciudad manualmente o conceder el permiso de ubicación aproximada. |
| Favoritos | Marcar o quitar noticias con la estrella. Cada cuenta ve los favoritos que guardó en ese dispositivo. |
| Descargas | Guardar el texto de una noticia y su portada cuando se pueden obtener del sitio original. La sección **Descargas** permite leer la copia, compartir el enlace y eliminarla. Las descargas se guardan por cuenta en el dispositivo. |
| Lectura y uso sin conexión | Puedes iniciar sesión con una cuenta local, abrir favoritos, leer artículos descargados y ver titulares o noticias cercanas previamente guardados en caché. Abrir el sitio original y consultar noticias nuevas requiere conexión. |
| Compartir | Enviar el título y el enlace de una noticia mediante el menú de compartir de Android. |

Las cuentas se crean y se usan en el dispositivo. Room guarda el registro y el hash de la contraseña. El formulario confirma el correo comparando los dos campos ingresados. Los favoritos, las descargas y las preferencias se asocian a la cuenta local.

## Configurar titulares diarios

1. En Inicio, toca la **campana junto a tu perfil**.
2. En **Hora de entrega**, toca **Cambiar** y elige una hora con el selector de reloj. Marca una o varias **categorías de interés**.
3. Pulsa **Guardar y activar**. Concede el permiso de notificaciones si Android lo solicita.
4. Abre el mismo modal para **Guardar** cambios o **Desactivar** el resumen.

La app programa el envío con la hora local del teléfono y consulta GNews en segundo plano cuando hay conexión. Android puede retrasar la entrega para ahorrar batería. El resumen puede llegar hasta cuatro horas después de la hora elegida y dentro del mismo día. Si ya recibiste el resumen, un cambio de hora se aplica a partir del día siguiente.

Al cambiar de cuenta, la app cancela la programación de la sesión saliente y la restablece cuando vuelves a iniciar sesión en esa cuenta.

## Estructura del proyecto

| Ruta | Responsabilidad |
| --- | --- |
| [`app/`](app/) | Módulo Android en Kotlin con vistas XML, View Binding y componentes Material. |
| [`app/src/main/java/com/example/newsapp/`](app/src/main/java/com/example/newsapp/) | Pantallas de acceso, Inicio, Favoritos y Descargas. `AuthenticatedActivity` comprueba la sesión y `NewsViewModel` controla la lista de noticias y mantiene una solicitud de carga activa a la vez. |
| [`data/`](app/src/main/java/com/example/newsapp/data/) | `NewsRepository` coordina titulares, búsquedas y caché. Incluye la autenticación local, las preferencias por cuenta y la descarga de artículos. |
| [`data/api/`](app/src/main/java/com/example/newsapp/data/api/) | Cliente Retrofit y OkHttp, rutas de GNews y modelos de respuesta. |
| [`local/`](app/src/main/java/com/example/newsapp/local/) | Base de datos Room y DAOs para cuentas, caché de noticias, favoritos y descargas. |
| [`weather/`](app/src/main/java/com/example/newsapp/weather/) | Selección de ciudad, ubicación aproximada y consultas de clima y geocodificación. |
| [`notifications/`](app/src/main/java/com/example/newsapp/notifications/) | Modal de configuración, programación con `AlarmManager` y consulta en segundo plano con `JobScheduler`. También reprograma el resumen al reiniciar el teléfono o cambiar su hora o zona horaria. |
| [`adapter/`](app/src/main/java/com/example/newsapp/adapter/) y [`res/`](app/src/main/res/) | Tarjetas de noticias, diseños de pantalla, textos, iconos y temas. |
| [`utils/`](app/src/main/java/com/example/newsapp/utils/) | Avisos, apertura de artículos en el navegador, compartición de enlaces y ajuste del contenido a las barras del sistema. |
| [`backend/`](backend/README.md) | Servidor Express para clientes anteriores, incluido el registro del cliente Ionic. La app Android actual funciona con cuentas locales y APIs directas. |
| [`gradle/`](gradle/) | Gradle Wrapper, catálogo de versiones y configuración de la JVM de Gradle. |
| [`app/src/test/`](app/src/test/) y [`app/src/androidTest/`](app/src/androidTest/) | Pruebas unitarias y pruebas instrumentadas para Android. |
| [`tasks/`](tasks/) | Plan y tareas de la migración a Ionic. |

La app consulta directamente a **GNews** por HTTPS para titulares, búsquedas, noticias cercanas y el resumen diario. **Open-Meteo** proporciona el clima y la búsqueda de ciudades. Con una clave de GNews válida en la compilación, basta que el teléfono tenga conexión para usar estas funciones.

## Ejecutar en un celular Android

Estos pasos usan **Windows, Android Studio y un teléfono Android conectado por USB** para instalar la app. Una vez instalada, la app puede consultar noticias usando Wi-Fi o datos móviles.

### 1. Preparar el equipo

- Instala **Android Studio** con soporte para Android Gradle Plugin **9.4.1**. En **SDK Manager**, instala la plataforma **Android 17 (API 37)** y **Android SDK Platform-Tools**.
- El proyecto usa **Gradle 9.6.0** mediante el Wrapper y su configuración solicita **JDK 25** para el daemon. Si Gradle no puede descargarlo automáticamente, instala un JDK 25 y selecciónalo como **Gradle JDK** en Android Studio.
- Ten a mano una [clave de GNews](https://gnews.io/dashboard) para compilar la app con acceso a titulares y búsquedas.
- Usa un teléfono con **Android 7.0 (API 24)** o posterior y un cable USB de datos. La API 37 es la versión del SDK de compilación. Consulta la [compatibilidad de AGP](https://developer.android.com/build/releases/about-agp) y la [guía para ejecutar en un dispositivo físico](https://developer.android.com/studio/run/device.html).

Clona el repositorio y abre la carpeta `App-CapNews` en Android Studio. Si ya tienes el proyecto en tu equipo, abre la carpeta que contiene `settings.gradle.kts`.

```powershell
git clone https://github.com/4ndr3w606/App-CapNews.git
Set-Location App-CapNews
```

### 2. Configurar GNews

Antes de sincronizar Gradle, agrega esta línea en `local.properties`, ubicado en la raíz del proyecto. Conserva la línea `sdk.dir` y reemplaza el valor de ejemplo por tu clave de GNews.

```properties
GNEWS_API_KEY=TU_CLAVE_DE_GNEWS
```

También puedes usar la variable de entorno `GNEWS_API_KEY`, que tiene prioridad sobre `local.properties`. Este archivo está excluido de Git. La compilación incorpora la clave al APK, del que se puede extraer y usar para consumir la cuota de GNews. Usa este flujo para la demostración académica.

### 3. Instalar y abrir la aplicación

1. Activa las **Opciones de desarrollador** y la **Depuración por USB** en el teléfono.
2. Conéctalo al computador, desbloquéalo y autoriza la depuración cuando Android lo solicite.
3. En Android Studio, selecciona el módulo **app**, la variante **debug** y el teléfono en el selector de dispositivos. Pulsa **Run**.
4. Regístrate e inicia sesión. Con Wi-Fi o datos móviles, abre la categoría **Mundo** y busca **Neiva** para comprobar la carga de noticias. Guarda o descarga un artículo para verificar el acceso sin conexión.

Si prefieres compilar e instalar por terminal, ejecuta desde la raíz del proyecto con el celular conectado y autorizado. Los comandos `adb` requieren que Platform-Tools esté en `PATH`.

```powershell
.\gradlew.bat :app:assembleDebug
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

El teléfono debe aparecer como `device` en `adb devices`. Después de instalar, abre la aplicación desde su icono. Consulta la [guía oficial de depuración por USB y detección de dispositivos](https://developer.android.com/studio/run/device.html).

### Compartir el APK para usarlo en otra red

1. En Android Studio, usa **Build → Generate Signed Bundle / APK**, elige **APK**, selecciona tu almacén de claves y genera la variante **release**. Conserva el mismo certificado de firma para futuras actualizaciones.
2. Instala el APK firmado en el celular. Si Android lo solicita, habilita temporalmente la instalación de aplicaciones para la fuente desde la que abriste el archivo. Consulta la [guía de firma de Android](https://developer.android.com/studio/publish/app-signing).

Quien instale el APK puede crear su cuenta local y consultar GNews con su conexión a internet. La configuración de la clave se hace en el equipo que compila el APK.

## Solución de problemas

| Síntoma | Qué revisar |
| --- | --- |
| Gradle solicita una clave válida | Configura `GNEWS_API_KEY` en `local.properties` o en el entorno y sincroniza Gradle. |
| Puedes iniciar sesión y los titulares no cargan | Comprueba la conexión del celular, la clave configurada al compilar y la cuota de GNews. Si cambias la clave, vuelve a compilar e instala el APK nuevo. |
| El resumen diario no llega | Comprueba que la cuenta esté activa, que tenga categorías seleccionadas y que Android permita las notificaciones de CapNews. Revisa también la conexión del celular y si esa cuenta ya recibió su resumen del día. |
| Android Studio no detecta el celular | Usa un cable de datos, desbloquea el teléfono, acepta la autorización de depuración e instala el controlador USB del fabricante si Windows lo requiere. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | El APK nuevo tiene una firma distinta. Vuelve a compilar con la misma clave de la app instalada para actualizarla y conservar sus datos. |
| Una noticia no se puede descargar | Algunos medios restringen el acceso o no ofrecen el texto en un formato que la aplicación pueda extraer. Si falla solo la portada, la descarga de texto puede completarse. |

## Pruebas

Ejecuta los siguientes comandos desde la raíz del proyecto.

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Las pruebas unitarias cubren el estado del listado, la cancelación de solicitudes, los cambios de categoría y la caché de noticias cercanas. Las pruebas instrumentadas cubren el aislamiento de datos por cuenta y la migración de favoritos. Para ejecutarlas necesitas un dispositivo o emulador Android conectado.
