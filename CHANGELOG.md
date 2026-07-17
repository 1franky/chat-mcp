# Changelog

Todos los cambios relevantes del proyecto se documentan aquí. El formato sigue Keep a Changelog y el proyecto aún no tiene una versión publicada.

## [Unreleased]

### Added — Proveedor MiniMax (2026-07-17)

- Nuevo tipo de proveedor `MINIMAX`: host fijo `https://api.minimax.io/v1`, autenticación bearer y Chat Completions compatible con OpenAI (streaming SSE incluido), siguiendo el mismo patrón que BytePlus. No publica catálogo de modelos, así que el model ID se configura manualmente como `CONFIGURED`.

### Added — Sprint 3

- Conversaciones y mensajes aislados por propietario con CRUD, busqueda, regeneracion y snapshots de proveedor/modelo.
- Streaming SSE y cancelacion por usuario, timeout o desconexion, conservando contenido parcial.
- Adaptadores streaming normalizados para OpenAI Responses, Anthropic Messages, BytePlus/OpenAI Chat Completions, compatibles, Ollama y fake.
- Uso opcional, `finishReason` y request ID de proveedor persistidos sin cuerpos ni secretos.
- UI completa de chat con historial, selectores, badges, estados, copiar, detener y errores recuperables.
- Markdown seguro con codigo y tablas, tema oscuro persistente y preferencia inicial del sistema.
- Migracion Flyway `V4`, pruebas de streaming/cancelacion/ownership, parser SSE, sanitizacion y E2E de chat fake.

### Security — Sprint 3

- Ownership aplicado en controlador, caso de uso y repositorio; una cuenta `ADMIN` no puede leer chats ajenos.
- Credenciales descifradas únicamente en backend y limpiadas de buffers mutables tras configurar la peticion.
- Historial, mensajes y respuestas tienen limites; SSE usa mismo origen, CSRF, no-store y buffering desactivado.
- HTML, handlers y esquemas de URL peligrosos no se renderizan desde respuestas Markdown.

### Changed — Sprint 3

- El estado MCP visible se etiqueta como fake; la conexion real y tool calling permanecen en Sprint 4.

### Added — Sprint 2

- Conexiones LLM aisladas por usuario para OpenAI, Anthropic, BytePlus, OpenAI-compatible, Ollama y fake.
- Cifrado AES-256-GCM con nonce aleatorio, AAD, pista enmascarada y version de clave.
- Prueba de conexion, descubrimiento best-effort, modelo manual/configurado y seleccion predeterminada.
- Capacidades triestado y origen `DISCOVERED`, `MANUAL` o `CONFIGURED` sin inferencias por nombre.
- UI Angular responsive para gestionar conexiones, estados, capacidades y modelos.
- Migracion Flyway `V3` y pruebas de ownership, cifrado, SSRF y contratos HTTP locales.

### Security — Sprint 2

- Allowlist de hosts para destinos configurables, HTTP interno explícito, bloqueo link-local y redirects desactivados.
- Timeout y limite de bytes para respuestas externas; errores normalizados sin cuerpos ni secretos.
- La clave maestra es obligatoria fuera de PostgreSQL y ninguna API devuelve material cifrado.

### Changed — Sprint 2

- El puerto host predeterminado de la interfaz web cambia de `8080` a `3000`.

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

### Not implemented after Sprint 3

- Tool calling, MCP remoto, tarjetas de tools, RAG, documentos y citas.
