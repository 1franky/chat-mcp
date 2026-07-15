# ADR-0006: Identidad, sesiones y bootstrap atomico

- Estado: Accepted
- Fecha: 2026-07-15

## Contexto

Sprint 1 necesita una aplicacion web first-party sin JWT en el navegador, un primer administrador
seguro ante concurrencia y administracion de usuarios sin conceder acceso a datos ajenos.

## Decision

Mantener identidad dentro del monolito hexagonal y persistirla en el schema `identity`. Usar UUID,
correo normalizado, roles cerrados `ADMIN`/`USER`, baja logica y version optimista. Asignar el
primer `ADMIN` dentro de una transaccion PostgreSQL `SERIALIZABLE` protegida por advisory lock
transaccional y reintentar abortos transitorios.

Usar Spring Security con Argon2id y Bouncy Castle, Spring Session JDBC, cookie de sesion
`HttpOnly`/`SameSite=Lax`/`Secure` configurable, CSRF de doble envio compatible con Angular y
rotacion del identificador al autenticar. Invalidar sesiones ante logout, baja o cambio de rol.

Proteger las operaciones administrativas por autoridad en el servidor. Las altas administrativas
siempre crean `USER`; degradar o desactivar al ultimo administrador activo se serializa con un lock
administrativo y se rechaza.

## Consecuencias

No hay tokens bearer que gestionar en el cliente y las revocaciones son inmediatas. La sesion y
los locks dependen de PostgreSQL, coherente con el despliegue actual. El rate limiting de Sprint 1
es local por instancia y direccion remota; un despliegue horizontal exigira un almacen compartido
y una politica explicita de proxies confiables. La baja logica conserva auditoria y evita reutilizar
correos sin una politica futura deliberada.
