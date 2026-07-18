# Changelog

Todos los cambios relevantes del proyecto se documentan aquí. El formato sigue Keep a Changelog y el proyecto aún no tiene una versión publicada.

## [Unreleased]

### Added — Base URL configurable para MiniMax (2026-07-18)

- `MiniMaxProviderAdapter` ahora acepta un `baseUrl` opcional por conexión (default `https://api.minimax.io/v1` si se omite), ya que MiniMax publica endpoints regionales distintos (p. ej. `api.minimaxi.com` en China). Un Base URL personalizado pasa por la misma `ProviderDestinationPolicy` (allowlist SSRF) que ya exigen OpenAI-compatible/Ollama.
- Nuevo campo "Base URL" en el formulario de proveedores cuando se selecciona MiniMax, con hint indicando el valor por defecto.
- Verificado manualmente contra el stack Docker Compose real: conexión sin Base URL (usa el default), conexión con Base URL personalizado (se guarda), y prueba de conexión bloqueada por `PROVIDER_DESTINATION_BLOCKED` cuando el host no está en la allowlist.

### Added — Sprint 5 RAG: endpoint de subida `POST /api/documents` (2026-07-17)

- `DocumentManagementUseCase`/`DocumentManagementService`: sube un documento validando tamaño máximo (`MAX_UPLOAD_BYTES`, ahora sí cableado a `spring.servlet.multipart.*`/`app.rag.upload.max-bytes`), MIME real cruzado contra la extensión declarada, protección ZIP-bomb para `.docx`, hash SHA-256 para idempotencia (`DocumentRepository.findByOwnerIdAndContentHash`, nuevo método), y aislamiento estricto por `owner_id`. El documento queda en `UPLOADED`.
- Nuevo port `DocumentMimeDetectionPort` con adaptador `TikaDocumentMimeDetectionAdapter` (`org.apache.tika:tika-core`).
- Endpoints `POST/GET /api/documents`, `GET /api/documents/{id}`, `DELETE /api/documents/{id}` (`DocumentController`), con 5 nuevos `@ExceptionHandler` en `ApiExceptionHandler` (404/413/415/500).
- Diferido explícitamente (documentado en `docs/rag.md`): protección XXE y límites de extracción (tareas de la futura extracción de texto), antivirus, endpoint de descarga.
- 24 tests nuevos (13 unitarios del servicio incluyendo zip-bomb, 5 del adaptador Tika, 4 de integración HTTP end-to-end, 2 de idempotencia por hash). `./mvnw verify` completo en verde (113/113).

### Added — Sprint 5 RAG: DocumentRepository/DocumentStoragePort/VectorSearchPort (2026-07-17)

- `DocumentRepository` ampliado (find/save/delete/paginación con filtro de estado) con adaptador JPA real `DocumentJpaAdapter` sobre `rag.document` y fake en memoria.
- `VectorSearchPort` con adaptador real `PgVectorSearchAdapter`: primer uso de JDBC nativo (`JdbcTemplate` + `com.pgvector:pgvector`) en `src/main`, ya que Hibernate no soporta el tipo `vector` en este stack. `index()` actualiza embeddings de chunks existentes; `search()` ordena por distancia coseno vía el índice HNSW.
- `DocumentStoragePort` con adaptador real `FilesystemDocumentStorageAdapter` sobre el volumen Docker `chat-documents`, con saneamiento anti path-traversal.
- Fakes deterministas en memoria para los tres ports, wireados junto a los reales tras `app.integrations.mode`.
- Nuevo modelo de dominio `Document`/`DocumentStatus`. Sin caso de uso ni endpoint todavía — resto del sprint (upload, extracción, chunking, retrieval, UI) sigue sin aprobar.
- 26 tests nuevos (17 unitarios + 9 de integración/migración contra Postgres real). `./mvnw verify` completo en verde (89/89).

### Added — Sprint 5 RAG: esquema y EmbeddingProviderPort (2026-07-17)

- Migración `V6` que corrige las restricciones `CHECK` de proveedor para incluir `MINIMAX` (bug detectado al preparar esta migración).
- Migración `V7`: esquema `rag` con `document`, `document_chunk` (vector 1536 + índice HNSW coseno) y `message_document` (relación `SELECTED`/`CITED`), con ownership, estados de procesamiento e idempotencia por hash.
- `EmbeddingProviderPort` con `EmbeddingBatch` (dimensión + vectores autovalidados) y `FakeEmbeddingProviderAdapter` determinista sin red, wireado tras `app.integrations.mode=fake`. Resto del sprint (upload, storage, retrieval, UI) sigue sin aprobar.

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
