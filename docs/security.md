# Seguridad de bootstrap

## Controles incluidos

- Spring Security permite únicamente `/api/system/status` y healthchecks; cualquier otro endpoint queda denegado.
- No se crea un usuario o contraseña por defecto y no existe login en Sprint 0.
- PostgreSQL no publica puerto al host.
- Backend, frontend y fake MCP se ejecutan como usuarios no-root, con filesystem de sólo lectura, `cap_drop: ALL`, `no-new-privileges` y temporales en `tmpfs`.
- PostgreSQL arranca y ejecuta como UID/GID 999 sin root, con filesystem de sólo lectura, `cap_drop: ALL`, `no-new-privileges`, volumen nombrado y temporales dedicados.
- Nginx aplica CSP, HSTS, `nosniff`, `DENY`, Referrer-Policy y Permissions-Policy. La terminación TLS corresponde al proxy de producción; HSTS sólo surte efecto bajo HTTPS.
- Los errores HTTP usan Problem Details sin mensajes internos ni stack traces.
- `.env.example` sólo contiene marcadores; `.env` está ignorado.

## Límites conscientes

- Las variables de registro, clave maestra y límites todavía no activan funciones: identidad comienza en Sprint 1 y cifrado/proveedores en Sprint 2.
- Spring Session JDBC está en el classpath para la arquitectura objetivo, pero su esquema y flujo se dejan desactivados hasta Sprint 1.
- No existe CSRF autenticado todavía. El diseño mismo-origen y Spring Security permitirán incorporarlo con sesiones en Sprint 1.
- Los adaptadores fake no reciben API keys ni realizan egress.

## Secretos

Nunca guardar `.env`, contraseñas, claves de proveedores o `CREDENTIAL_MASTER_KEY` en Git. Producción debe inyectarlos desde un gestor de secretos y rotarlos de forma independiente.

## Pendiente de threat modeling

Antes de Sprint 1 se debe revisar formalmente sesiones, cookies, CSRF, registro concurrente del primer admin, rate limiting y auditoría. Antes de habilitar MCP remoto deben definirse allowlists de tools, límites de respuesta, timeouts y aprobación explícita de operaciones sensibles.
