# Migración de CapNews a Ionic, Angular y TypeScript

## Objetivo y alcance

Reconstruir el cliente móvil como una aplicación Ionic con Angular standalone y TypeScript. Android es la primera plataforma de entrega. El navegador sirve para iterar y comprobar el cliente. Una entrega para iOS o web pública queda para otra etapa.

Continuar sobre el proyecto Ionic existente en `C:\AndroidApps\CapNews`. Ya tiene Angular standalone, TypeScript, Capacitor y un archivo de bloqueo. Usar la app Kotlin de `C:\AndroidApps\NewsApp\app` como referencia hasta completar la comparación en Android. Conservar el servicio de autenticación en `C:\AndroidApps\NewsApp\backend`, sus rutas y la base SQLite de cuentas. También se puede reutilizar el logo de CapNews.

## Mapa de la migración

| Función actual | Destino |
|---|---|
| Pantallas y navegación Android | Páginas Ionic, componentes standalone y Angular Router |
| Registro y sesión | Formularios reactivos y Angular HttpClient contra `/api/auth/register`, `/api/auth/login`, `/api/auth/me` y `/api/auth/logout` |
| Titulares por categoría y búsqueda | Servicio Angular con tipos de artículo, estado de pantalla y una fuente de datos separada de la UI |
| Favoritos y caché Room | Almacenamiento local con favoritos por identificador de cuenta y caché por categoría |
| Sesión cifrada y lector de artículos | Almacenamiento seguro del sistema y apertura de enlaces HTTPS mediante Capacitor |

## Decisiones y riesgos

- **Versiones.** El manifiesto declara Angular 22.2.6, Ionic 9 y Capacitor 8.6.2. El equipo tiene Node 26.7.0 y npm 11.19.0. Confirmar la instalación y la compilación con el archivo de bloqueo existente antes de cambiar versiones.
- **Cuentas y conexión local.** Reutilizar las cuentas del backend existente. Durante el desarrollo web, dirigir `/api/auth` al backend mediante el proxy de Angular. Para Android, definir una URL alcanzable desde el dispositivo. Usar HTTPS en los APK compartidos. La validación de las sesiones sigue a cargo del servidor.
- **Noticias.** Usar datos de ejemplo para desarrollar la interfaz. Antes de conectar noticias reales en Ionic, incorporar un intermediario en el backend que conserve la clave de GNews en el servidor. Una clave en `environment.ts` o enviada desde Angular quedaría disponible en el código entregado al cliente.
- **Sin conexión.** Con el comportamiento actual, el arranque valida la sesión contra el backend. Antes de cerrar la migración, decidir si una sesión previamente validada podrá abrir noticias y favoritos locales sin red y bajo qué condición de expiración.
- **Instalaciones existentes.** Usar un identificador distinto para probar ambas apps en paralelo. Si se quiere actualizar la app instalada, confirmar el mismo identificador, una firma compatible y un código de versión mayor antes de distribuir el APK. Room no se importa automáticamente. Si hay que conservar los favoritos históricos, acordar cómo importarlos y a qué cuenta asignarlos, dado que la tabla actual no identifica a su dueño.

## Orden y puntos de control

La base web desbloquea el formulario y una prueba temprana del contenedor Android. Registro e inicio de sesión desbloquean los favoritos por cuenta. Titulares reales desbloquean búsqueda, lector y caché. El cierre de la migración depende de probar esos recorridos en un dispositivo.

- **Primer hito:** plantilla Ionic y pantalla de acceso visibles en el navegador. Compilación web correcta.
- **Segundo hito:** registro, acceso y cierre de sesión contra el backend, con una prueba temprana del APK en Android.
- **Tercer hito:** titulares reales, búsqueda, lector, favoritos por cuenta y caché probados antes de distribuir.

La secuencia de trabajo y los criterios detallados están en [todo.md](todo.md).

## Referencias

- [CLI de Ionic y plantilla Angular standalone](https://ionicframework.com/docs/cli/commands/start)
- [Compatibilidad de Angular, Node y TypeScript](https://angular.dev/reference/versions)
- [Instalación y sincronización de Capacitor](https://capacitorjs.com/docs/getting-started)
- [Plataforma Android en Capacitor](https://capacitorjs.com/docs/android)
