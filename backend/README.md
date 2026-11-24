# Backend de CapNews

La app consulta las noticias mediante este backend. La clave de GNews se configura con
`GNEWS_API_KEY` en `backend/.env` o en las variables del servidor. El APK contiene
la URL del backend y usa el token de sesión del usuario. El servidor carga `.env`
automáticamente cuando existe.

## Noticias

`GET /api/news/top-headlines?category=world&lang=es` y
`GET /api/news/search?q=Neiva&lang=es&country=co&sortby=relevance` requieren
`Authorization: Bearer <token>`. Las búsquedas incluyen título, descripción y cuerpo.
Cada cuenta puede realizar hasta 30 consultas por minuto. Los errores del proveedor
se devuelven con mensajes genéricos.

## HTTPS

El despliegue incluido usa Docker Compose y Caddy sobre un servidor Linux con
dominio público. Caddy obtiene y renueva los certificados HTTPS.

1. Instala Docker Engine y Docker Compose en el servidor.
2. Copia esta carpeta al servidor y crea `.env` a partir de `.env.example`.
   Define `GNEWS_API_KEY`, `CAPNEWS_DOMAIN` con el dominio sin protocolo
   y `ACME_EMAIL` con tu correo para los certificados.
3. Apunta los registros DNS del dominio al servidor y habilita los puertos 80 y 443.
4. Desde esta carpeta ejecuta `docker compose up -d --build`.
5. Comprueba `https://TU_DOMINIO/health` y configura en `local.properties`
   `authApiBaseUrl=https://TU_DOMINIO/` antes de generar el APK.

Con Docker Compose, SQLite queda en el sistema de archivos del contenedor de la API.
Al eliminar o recrear ese contenedor se pierden sus cuentas y sesiones. Los volúmenes
de Caddy guardan únicamente los certificados y la configuración de HTTPS.

API propia para crear cuentas, iniciar y cerrar sesión. Usa SQLite para guardar cuentas y sesiones. Las contraseñas se derivan con `scrypt`; la base de datos guarda el hash, nunca la contraseña original. El cliente recibe un token aleatorio y el servidor guarda únicamente su SHA-256.

## Requisitos y ejecución local

Requiere Node.js 22 o superior.

```powershell
Set-Location C:\AndroidApps\NewsApp\backend
npm.cmd install
$env:PORT = '3000'
npm.cmd start
```

Ejecuta estos comandos en PowerShell de Windows o en la terminal de Android Studio. Mantén abierta la terminal después de ver `API de autenticación escuchando en el puerto 3000.` Puedes comprobar el servicio desde otra terminal con `Invoke-RestMethod http://127.0.0.1:3000/health`. Debe devolver `status` con valor `ok`.

Para comprobar la conexión desde el emulador, abre `http://10.0.2.2:3000/health` en su navegador. Si devuelve `{"status":"ok"}`, vuelve a CapNews e intenta registrarte.

En Android 17 o posterior, la app de depuración solicita acceso a la red local al registrarte o iniciar sesión contra el backend HTTP. Si denegaste el permiso, puedes habilitarlo en los ajustes de permisos de CapNews en el emulador.

La base se crea en `backend/data/capnews.sqlite`. Esa carpeta está excluida de Git. Puedes cambiar la ruta con `AUTH_DB_PATH`.

`backend/.env.example` documenta las variables. Copia sus valores a `backend/.env` o define las variables en el entorno del proceso o en el panel de configuración del proveedor.

En el emulador Android, el build `debug` apunta a `http://10.0.2.2:3000/`. Para un teléfono físico, configura `authApiBaseUrl` con una dirección alcanzable desde el teléfono, por ejemplo en `local.properties`:

```properties
authApiBaseUrl=http://192.168.1.20:3000/
```

La variante debug permite HTTP para pruebas locales. En una versión compartida o de producción configura una URL HTTPS:

```properties
authApiBaseUrl=https://tu-dominio.example/
```

Para una versión release, usa un servidor accesible públicamente mediante HTTPS.

Si el servicio está detrás de un proxy inverso de confianza, configura `TRUST_PROXY_HOPS` con el número exacto de proxies para que el límite de intentos use la IP del cliente. No lo habilites sin conocer la topología del despliegue.

## Registro desde Ionic

Con el backend iniciado en el puerto 3000, abre PowerShell en `C:\AndroidApps\CapNews`.

- **Navegador:** ejecuta `npm start -- --proxy-config proxy.conf.json`.
- **Emulador Android:** ejecuta `npm run build -- --configuration development` y `npx cap sync android`. Vuelve a ejecutar la variante debug desde Android Studio. El código de desarrollo se conecta al backend en `http://10.0.2.2:3000`.

Para un APK de producción, configura `registrationApiUrl` en `src/environments/environment.prod.ts` con la URL HTTPS del backend. Si se aloja la versión web, sirve `/api/auth` desde el mismo origen que la app, mediante el backend o un proxy inverso.

## API

Todas las respuestas de error usan `{ "error": { "code": "...", "message": "..." } }`.

- `POST /api/auth/register` con `{ "name": "...", "email": "...", "password": "...", "termsVersion": 1 }` crea una cuenta y devuelve `user`, `token` y `expiresAt`. El nombre de usuario admite hasta 120 caracteres después de quitar los espacios iniciales y finales. El cliente envía `termsVersion` después de obtener la aceptación explícita. El servidor exige la versión vigente y guarda la versión y la fecha de aceptación en milisegundos desde la época Unix.
- `POST /api/auth/login` con `email` y `password` autentica y crea una sesión.
- `GET /api/auth/me` acepta `Authorization: Bearer <token>` y devuelve el usuario actual y `expiresAt`, el vencimiento de la sesión en milisegundos desde la época Unix.
- `POST /api/auth/logout` acepta `Authorization: Bearer <token>` y revoca la sesión.
- `GET /health` informa si el proceso está activo.

La contraseña debe tener entre 8 y 128 caracteres. Las sesiones vencen en siete días y el backend limita los intentos de autenticación. No se implementan todavía verificación de correo ni recuperación de contraseña.
