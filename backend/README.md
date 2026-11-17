# API de autenticación de CapNews

API propia para crear cuentas, iniciar y cerrar sesión. Usa SQLite para guardar cuentas y sesiones. Las contraseñas se derivan con `scrypt`; la base de datos guarda el hash, nunca la contraseña original. El cliente recibe un token aleatorio y el servidor guarda únicamente su SHA-256.

## Requisitos y ejecución local

Requiere Node.js 22 o superior.

```powershell
cd backend
npm install
$env:PORT = "3000"
npm start
```

La base se crea en `backend/data/capnews.sqlite`. Esa carpeta está excluida de Git. Puedes cambiar la ruta con `AUTH_DB_PATH`.

`backend/.env.example` documenta las variables; el servidor no carga ese archivo automáticamente. Define las variables en el entorno del proceso o en el panel de secretos/configuración del proveedor.

En el emulador Android, el build `debug` apunta a `http://10.0.2.2:3000/`. Para un teléfono físico, configura `authApiBaseUrl` con una dirección alcanzable desde el teléfono, por ejemplo en `local.properties`:

```properties
authApiBaseUrl=http://192.168.1.20:3000/
```

La variante debug permite HTTP para pruebas locales. En una versión compartida o de producción configura una URL HTTPS:

```properties
authApiBaseUrl=https://tu-dominio.example/
```

No despliegues una versión release con HTTP. Usa un servidor accesible públicamente, HTTPS y almacenamiento persistente para el archivo SQLite. SQLite sirve para una instancia pequeña; una infraestructura con varias instancias debería usar una base de datos compartida.

Si el servicio está detrás de un proxy inverso de confianza, configura `TRUST_PROXY_HOPS` con el número exacto de proxies para que el límite de intentos use la IP del cliente. No lo habilites sin conocer la topología del despliegue.

## API

Todas las respuestas de error usan `{ "error": { "code": "...", "message": "..." } }`.

- `POST /api/auth/register` con `{ "email": "...", "password": "..." }` crea una cuenta y devuelve `user`, `token` y `expiresAt`.
- `POST /api/auth/login` con el mismo cuerpo autentica y crea una sesión.
- `GET /api/auth/me` acepta `Authorization: Bearer <token>` y devuelve el usuario actual.
- `POST /api/auth/logout` acepta `Authorization: Bearer <token>` y revoca la sesión.
- `GET /health` informa si el proceso está activo.

La contraseña debe tener entre 8 y 128 caracteres. Las sesiones vencen en siete días y el backend limita los intentos de autenticación. No se implementan todavía verificación de correo ni recuperación de contraseña.
