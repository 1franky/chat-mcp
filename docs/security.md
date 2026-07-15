# Seguridad

## Identidad y sesiones

- Spring Security autentica con correo normalizado y contrasenas Argon2id.
- Spring Session JDBC persiste el estado server-side en PostgreSQL; el navegador solo recibe un
  identificador opaco.
- La cookie `AI_DATA_CHAT_SESSION` es `HttpOnly`, `SameSite=Lax` y configurable como `Secure`.
- El identificador de sesion cambia al autenticar y logout invalida realmente la sesion.
- El principal persistido en sesion borra el hash de contrasena antes de serializarse.
- Angular no guarda JWT, cookies, contrasenas ni secretos en `localStorage`/`sessionStorage`.

## CSRF y autorizacion

El repositorio CSRF usa cookie `XSRF-TOKEN` y header `X-XSRF-TOKEN` con el handler recomendado
para SPA de Spring Security. La cookie CSRF debe configurarse como `Secure` en produccion. Los
tokens CSRF se eliminan al autenticar y cerrar sesion; la siguiente consulta de bootstrap emite uno
nuevo. Los healthchecks, estado y bootstrap son publicos; registro y login son publicos pero siguen exigiendo
CSRF. `/api/admin/**` requiere `ROLE_ADMIN`; el resto no permitido se deniega.

El rol nunca se acepta en registro o alta administrativa. El servidor asigna el primer `ADMIN` y
despues `USER`. El conteo y cambio del ultimo administrador se serializan para impedir carreras.
Ser administrador solo habilita gestion de usuarios y no otorga acceso a recursos privados de
otras cuentas.

## Abuso y errores

- Login permite por defecto 5 solicitudes por minuto y registro 10 por hora, por ruta y direccion
  remota. Al exceder devuelve `429` y `Retry-After`.
- Los limites se configuran con `LOGIN_RATE_LIMIT_*` y `REGISTRATION_RATE_LIMIT_*`.
- El limite actual reside en memoria por instancia. Antes de escalar horizontalmente debe migrarse
  a un almacen compartido y definirse una frontera de proxies confiables.
- Nginx sobrescribe `X-Forwarded-For` con la direccion del socket cliente; no concatena valores
  aportados por Internet. El backend no debe exponerse como un segundo punto de ingreso.
- Los fallos de login usan un mensaje generico para no enumerar usuarios.
- Problem Details no expone stack traces, hashes ni mensajes internos.
- La auditoria registra metadata acotada y nunca contrasenas, cookies, tokens o cuerpos completos.

## Despliegue

- PostgreSQL no publica puerto al host.
- Backend, frontend y fake MCP se ejecutan como usuarios no-root, con filesystem de solo lectura,
  `cap_drop: ALL`, `no-new-privileges` y temporales en `tmpfs`.
- Nginx aplica CSP, HSTS, `nosniff`, `DENY`, Referrer-Policy y Permissions-Policy.
- En produccion se exige HTTPS y deben activarse `SESSION_COOKIE_SECURE=true` y
  `CSRF_COOKIE_SECURE=true`.
- `.env.example` solo contiene marcadores; `.env` permanece ignorado.

## Limites conscientes

- El rate limiting local no coordina replicas. La topologia asume que toda API de navegador entra
  por Nginx; otro servicio conectado directamente a la red interna queda fuera de esa frontera.
- No existe recuperacion de contrasena, MFA ni politica de expiracion de credenciales en Sprint 1.
- Las bajas son logicas y no permiten reutilizar correo; una politica de borrado completo requiere
  una decision futura.
- Cifrado de credenciales de proveedores empieza en Sprint 2.
- MCP remoto sigue deshabilitado y sin autenticacion; solo backend pertenece a `ai-platform`.

## Secretos

Nunca guardar `.env`, contrasenas, claves de proveedores o `CREDENTIAL_MASTER_KEY` en Git.
Produccion debe inyectarlos desde un gestor de secretos y rotarlos de forma independiente.
