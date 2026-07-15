# Changelog

Todos los cambios relevantes del proyecto se documentan aquí. El formato sigue Keep a Changelog y el proyecto aún no tiene una versión publicada.

## [Unreleased]

### Added — Sprint 1

- Registro publico configurable y bootstrap atomico del primer `ADMIN`.
- Login/logout con Spring Security, Argon2id y sesiones server-side en PostgreSQL.
- Cookies de sesion `HttpOnly`, `SameSite=Lax`, atributo `Secure` configurable y CSRF mismo-origen.
- Roles `ADMIN`/`USER`, autorizacion de endpoints y proteccion del ultimo administrador activo.
- API y UI administrativa para listar, crear, promover, degradar y dar de baja usuarios.
- Auditoria segura para registro, login, logout, altas, bajas y cambios de rol.
- Rate limiting local con `429` para login y registro.
- UI Angular para primer administrador, registro, login, logout, guards y administracion.
- Pruebas Testcontainers de identidad y concurrencia sobre PostgreSQL/pgvector real.
- Pruebas Vitest y Playwright de los flujos de identidad.

### Security — Sprint 1

- Rotacion del identificador al autenticar e invalidacion real de sesiones.
- Hash de contrasenas Argon2id mediante Spring Security y Bouncy Castle.
- Mensajes de login genericos, respuestas Problem Details y JSON sin hashes.
- Invalidacion de todas las sesiones de un usuario tras cambio de rol o baja.

### Added — Sprint 0

- Monorepo con backend Spring Boot 4.1 y frontend Angular 22.
- Arquitectura hexagonal con puertos de LLM, MCP, modelos, embeddings, almacenamiento, búsqueda, cifrado y repositorios.
- Endpoint mínimo `/api/system/status` y healthchecks de liveness/readiness.
- PostgreSQL 18.4 con pgvector 0.8.2 y migración Flyway inicial.
- Imágenes multi-stage no-root y Compose con red interna más `ai-platform` externa.
- Fake LLM en proceso y fake MCP en proceso, más fixture HTTP WireMock.
- Pruebas JUnit, Testcontainers, ArchUnit y Vitest; lint y formato para ambos stacks.
- Configuración de Playwright y CI.
- Documentación operativa, de seguridad, integraciones, versiones y ADRs iniciales.

### Security

- PostgreSQL sin puerto público y servicios con privilegios reducidos donde es viable.
- Nginx con headers defensivos y proxy mismo-origen preparado para streaming.
- Endpoints no incluidos en el bootstrap denegados por Spring Security.
- Sin secretos reales ni llamadas a proveedores pagados.

### Not implemented after Sprint 1

- Chat, proveedores reales, cifrado de credenciales, MCP remoto y RAG.
