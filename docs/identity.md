# Identidad y usuarios

## Modelo

Sprint 1 admite exclusivamente los roles `ADMIN` y `USER`. La identidad usa UUID, correo
normalizado para unicidad/autenticacion, nombre visible, hash Argon2id, estado activo, version
optimista y timestamps UTC. La baja es logica para conservar trazabilidad y evitar reutilizacion
ambigua de una identidad auditada.

El primer registro en una base vacia obtiene `ADMIN`. El caso de uso ejecuta la comprobacion y el
alta dentro de una transaccion `SERIALIZABLE`, precedida por un advisory lock transaccional de
PostgreSQL. Los abortos serializables transitorios se reintentan hasta tres veces. Los registros
posteriores y todas las altas administrativas obtienen `USER`; el cliente nunca envia el rol al
crear una cuenta.

`ALLOW_PUBLIC_REGISTRATION=false` cierra los registros posteriores, pero mantiene disponible el
bootstrap inicial. Despues solo un administrador puede crear usuarios.

## API

| Metodo | Ruta | Acceso | Resultado |
| --- | --- | --- | --- |
| `GET` | `/api/auth/bootstrap` | Publico | Estado de bootstrap, registro y sesion; inicializa la cookie CSRF. |
| `POST` | `/api/auth/register` | Publico + CSRF | Crea la primera cuenta o un `USER` y abre sesion. |
| `POST` | `/api/auth/login` | Publico + CSRF | Autentica y crea/rota la sesion. |
| `POST` | `/api/auth/logout` | Autenticado + CSRF | Audita e invalida la sesion. |
| `GET` | `/api/auth/me` | Autenticado | Perfil publico de la cuenta actual. |
| `GET` | `/api/admin/users` | `ADMIN` | Lista paginada; `size` se limita a 100. |
| `POST` | `/api/admin/users` | `ADMIN` + CSRF | Crea siempre un `USER`. |
| `PATCH` | `/api/admin/users/{id}/role` | `ADMIN` + CSRF | Cambia entre `ADMIN` y `USER`. |
| `DELETE` | `/api/admin/users/{id}` | `ADMIN` + CSRF | Desactiva sin borrar la auditoria. |

Las respuestas de usuario no incluyen `passwordHash`, correo normalizado ni atributos internos.
Un cambio de rol o baja invalida todas las sesiones del usuario. No es posible degradar o dar de
baja al ultimo administrador activo.

## Sesion web

Spring Session JDBC persiste sesiones en `identity.spring_session` y
`identity.spring_session_attributes`. El principal serializado no contiene el hash de contrasena.
La cookie `AI_DATA_CHAT_SESSION` es `HttpOnly`, `SameSite=Lax` y `Secure` cuando
`SESSION_COOKIE_SECURE=true`. Angular usa la cookie `XSRF-TOKEN` y el header `X-XSRF-TOKEN`; no
guarda JWT ni secretos en storage web.

El cliente vuelve a consultar bootstrap despues de autenticar o cerrar sesion para obtener una
nueva cookie CSRF. Si un endpoint protegido devuelve `401`, Angular descarta su estado local y
redirige al acceso; `403` y `429` se presentan sin perder el detalle seguro del servidor.

## Auditoria

`audit.security_audit_event` registra actor, objetivo, tipo, exito, timestamp, direccion remota y
metadata acotada. Se cubren registros, login exitoso/fallido, logout, altas administrativas,
cambios de rol y bajas. No se persisten contrasenas, hashes, cookies, CSRF tokens ni cuerpos de
peticion.
