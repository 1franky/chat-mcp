# Tareas


Actualizado: 2026-07-16.

## Sprint 0 — Descubrimiento y bootstrap

- [x] Auditar repositorio, instrucciones `AGENTS.md` y estado inicial de Git.
- [x] Verificar versiones estables en fuentes oficiales y documentar la selección.
- [x] Crear monorepo con backend Spring Boot y frontend Angular mínimos.
- [x] Definir límites hexagonales y comprobarlos con ArchUnit.
- [x] Configurar PostgreSQL 18 con pgvector y migración Flyway reproducible.
- [x] Crear Compose base y overlay de desarrollo, redes, healthchecks y hardening inicial.
- [x] Añadir adaptadores fake deterministas para LLM y MCP.
- [x] Añadir fake MCP HTTP contractual sin operaciones de datos.
- [x] Configurar Maven Wrapper, JUnit, Testcontainers, Checkstyle y Spotless.
- [x] Configurar ESLint, Prettier, Vitest y Playwright.
- [x] Añadir CI para calidad, pruebas y builds multi-arquitectura.
- [x] Documentar arquitectura, seguridad, integraciones, versiones y ADRs.
- [x] Verificar builds, pruebas automatizadas, Compose y smoke tests del Sprint 0.

## Sprint 1 — Identidad y usuarios

- [x] Crear esquema Flyway para usuarios, auditoria y Spring Session JDBC.
- [x] Implementar primer administrador atomico con transaccion serializable y advisory lock.
- [x] Implementar registro publico configurable; altas administrativas siempre con rol `USER`.
- [x] Implementar login/logout con Argon2id, sesiones server-side y rotacion de identificador.
- [x] Configurar cookies `HttpOnly`, `SameSite`, `Secure` configurable y proteccion CSRF para SPA.
- [x] Aplicar rate limiting local a login y registro con respuestas `429`.
- [x] Implementar roles `ADMIN`/`USER`, autorizacion y guards de Angular.
- [x] Implementar listado, alta, cambio de rol y baja logica de usuarios.
- [x] Proteger al ultimo administrador activo ante degradacion o baja.
- [x] Invalidar sesiones al cambiar rol, dar de baja o cerrar sesion.
- [x] Auditar registro, login, logout, altas, bajas y cambios de rol sin secretos.
- [x] Probar primer admin, concurrencia, usuarios posteriores, `403` y ultimo admin con PostgreSQL real.
- [x] Probar UI de registro/login, bootstrap inicial, guards, errores y panel administrativo.
- [x] Mantener LLM/MCP en modo fake sin llamadas pagadas.

## Sprint 2 — Proveedores y modelos

- [x] Crear migracion de conexiones y modelos con ownership, constraints e indices.
- [x] Cifrar credenciales con AES-256-GCM, nonce aleatorio, AAD y version de clave.
- [x] Ocultar claves, ciphertext y nonce en JSON, errores, logs y auditoria.
- [x] Implementar adaptadores OpenAI, Anthropic, BytePlus, OpenAI-compatible, Ollama y fake.
- [x] Implementar prueba de conexion y normalizacion segura de errores/request IDs.
- [x] Implementar descubrimiento best-effort, modelo configurado/manual y seleccion predeterminada.
- [x] Representar capacidades por adaptador/modelo como `SUPPORTED`, `UNSUPPORTED` o `UNKNOWN`.
- [x] Aplicar allowlist SSRF, HTTPS por defecto, bloqueo link-local, redirects, timeouts y bytes.
- [x] Probar cifrado, manipulacion, ownership, secretos ausentes, SSRF y contratos HTTP sin APIs reales.
- [x] Crear UI responsive de proveedores/modelos con estados, acciones y errores recuperables.
- [x] Cambiar el puerto host de despliegue de la interfaz web a `3000`.
- [x] Actualizar README, CHANGELOG, documentos y ADR-0007.

## Sprint 3 — Chat

- [x] Crear migracion de conversaciones y mensajes con ownership, secuencia y una sola generacion activa.
- [x] Implementar crear, listar, buscar, abrir, renombrar y eliminar conversaciones propias.
- [x] Persistir mensajes, regeneraciones, parciales y snapshots inmutables de proveedor/modelo.
- [x] Implementar streaming SSE con heartbeat, timeout, desconexion y cancelacion explícita.
- [x] Guardar uso opcional, `finishReason` y request ID acotado cuando el proveedor los entrega.
- [x] Normalizar streaming de OpenAI, Anthropic, BytePlus, OpenAI-compatible, Ollama y fake.
- [x] Mantener historial acotado y no enviar prompts ni cuerpos de proveedor a logs o auditoria.
- [x] Crear UI completa de chat con historial, busqueda, selector, capacidades y estado de conexion.
- [x] Añadir detener, copiar, regenerar, renombrar, eliminar con confirmacion y errores recuperables.
- [x] Renderizar Markdown, codigo y tablas sin HTML arbitrario ni URLs peligrosas.
- [x] Añadir boton accesible de tema claro/oscuro, persistencia local y `prefers-color-scheme` inicial.
- [x] Identificar MCP como fake y reservar conexion real, tools y tarjetas para Sprint 4.
- [x] Añadir pruebas backend, frontend y E2E sin APIs pagadas, más documentos y ADR-0008.

## Sprint 4 — Cliente MCP real y tool calling

- [x] Implementar cliente MCP Streamable HTTP propio (`adapters/out/mcp/`) calcado del patron de adaptadores de proveedores LLM, seleccionable via `app.mcp.mode`/`MCP_INTEGRATION_MODE` independiente de `app.integrations.mode`.
- [x] Negociar `initialize` → `notifications/initialized` → `tools/list`, cachear el catalogo, validar el major de `contract_version` y exponer estado `UP`/`DEGRADED`/`DOWN` que nunca lanza excepcion.
- [x] Aplicar SSRF guard sobre el host fijo de MCP y ejecutar todas las llamadas bloqueantes en un executor dedicado, nunca en el hilo Reactor Netty del stream.
- [x] Exponer `GET /api/mcp/status` y `GET /api/mcp/tools`, y un panel de solo lectura `/settings/mcp` con el estado, contrato negociado y catalogo de tools.
- [x] Persistir tool calls en tabla hija `chat.message_tool_call` (migracion `V5`) sin modificar `chat.message` ni sus invariantes de generacion unica activa.
- [x] Orquestar tool calling multi-proveedor en `ChatService` para OpenAI y Anthropic (unicos con `toolCalling=SUPPORTED`): allowlist contra el catalogo descubierto, limite de rondas (`MAX_TOOL_ROUNDS`) y de tamano de resultado (`MAX_TOOL_RESULT_BYTES`), sin reescribir nunca `isError:true` como exito.
- [x] Traducir tool calling en `ProviderStreamingSupport` para los formatos OpenAI Responses y Anthropic Messages sin tocar BytePlus/OpenAI-compatible/Ollama.
- [x] Mostrar tarjetas de tool call en el chat con estado (pendiente/ejecutando/completado/error/bloqueado/tiempo agotado).
- [x] Anadir pruebas de contrato/seguridad (WireMock, JDK HttpServer, Testcontainers) y E2E Playwright sin llamadas pagadas ni al MCP real.
- [x] Documentar la decision en ADR-0009 y actualizar `docs/mcp-integration.md`, `docs/architecture.md`, `docs/security.md`, `README.md` y `CLAUDE.md`.

## Sprint 5 — RAG (iniciado 2026-07-17 con aprobacion explicita del propietario)

- [x] Migracion `V6` que corrige las restricciones `CHECK` de `chat.provider_connection`/`chat.message` para incluir `MINIMAX` (bug detectado al arrancar este sprint, ver seccion de MiniMax mas arriba).
- [x] Migracion `V7` con el esquema `rag`: `rag.document` (ownership, hash unico por propietario, estado de procesamiento, dimension/modelo de embedding obligatorios solo en `READY`), `rag.document_chunk` (`owner_id` denormalizado, `embedding vector(1536)` con indice HNSW coseno, texto acotado, pagina/seccion para citas) y `rag.message_document` (relacion `SELECTED`/`CITED` con indices unicos parciales).
- [x] `EmbeddingProviderPort` con `EmbeddingBatch` (dimension + vectores, valida longitud) y `FakeEmbeddingProviderAdapter` determinista (modelo `fake-embedding-v1`, vector unitario por hash SHA-256, sin red), wireado tras `app.integrations.mode=fake` igual que el resto de fakes.
- [x] Pruebas: `FakeEmbeddingProviderAdapterTest` (determinismo, dimension, rechazo de modelo desconocido y de longitud invalida); `PostgresMigrationTest` ampliado para verificar las tablas `rag.*`, la dimension del vector (`atttypmod = 1536`), el indice HNSW y las columnas `owner_id` obligatorias. Ejecutado contra Postgres real via Testcontainers el 2026-07-17 (2/2 en verde); `./mvnw verify` completo del backend tambien en verde (63/63).
- [x] `DocumentStoragePort`/`VectorSearchPort`/`DocumentRepository` reales y fake (aprobado explicitamente por el propietario el 2026-07-17, sin caso de uso ni endpoint todavia — solo la capa de persistencia/storage/vector-search):
  - `DocumentRepository` ampliado (`findByIdAndOwnerId`, `findAllByOwnerId` paginado con filtro de estado, `save`, `deleteByIdAndOwnerId`) con adaptador JPA real `DocumentJpaAdapter` (`rag.document`) y fake en memoria `FakeDocumentRepository`.
  - `VectorSearchPort` con adaptador real `PgVectorSearchAdapter`: JDBC nativo (`JdbcTemplate` + libreria `com.pgvector:pgvector`, primer uso de JDBC directo en `src/main`, ya que Hibernate no soporta el tipo `vector` en este stack) — `index()` actualiza (`UPDATE`) el embedding de chunks ya existentes (no crea filas, eso es tarea del futuro chunking), `search()` ordena por distancia coseno via el indice HNSW, `deleteByDocument()` borra los chunks. Fake en memoria `FakeVectorSearchAdapter` con el mismo criterio de score (`1 - distancia coseno`).
  - `DocumentStoragePort` con adaptador real `FilesystemDocumentStorageAdapter` (volumen Docker `chat-documents`, ruta configurable `app.rag.storage.path`/`RAG_STORAGE_PATH`, saneamiento anti path-traversal) y fake en memoria `FakeDocumentStorageAdapter`.
  - Los seis adaptadores (3 reales + 3 fake) son beans condicionales por `app.integrations.mode` en `ApplicationBeansConfiguration`, igual que LLM/Embedding.
  - Nuevo modelo de dominio `Document`/`DocumentStatus`; `DocumentChunk`/`MessageDocument` deliberadamente no se crearon como codigo (ningun port los expone todavia — ver `docs/rag.md`).
  - Pruebas: 17 tests unitarios (fakes + `FilesystemDocumentStorageAdapterTest` con `@TempDir`) y 9 de integracion/migracion contra Postgres real via Testcontainers (`DocumentJpaAdapterIntegrationTest`, `PgVectorSearchAdapterIntegrationTest`, `PostgresMigrationTest`), incluyendo aislamiento por owner, constraint unico `owner_id+content_hash`, optimistic locking y borrado en cascada. `./mvnw verify` completo en verde (89/89).
- [x] Endpoint de subida `POST /api/documents` con sus protecciones (aprobado explicitamente por el propietario el 2026-07-17):
  - `DocumentManagementUseCase`/`DocumentManagementService`: valida tamano maximo configurable (`MAX_UPLOAD_BYTES`, ahora si cableado a `spring.servlet.multipart.*` y `app.rag.upload.max-bytes` — antes existia la variable en `compose.yaml` pero nada la leia), MIME real + extension mediante `DocumentMimeDetectionPort`/`TikaDocumentMimeDetectionAdapter` (nuevo, `org.apache.tika:tika-core`, primera dependencia externa nueva de este tipo, confinada a un adaptador), nombre de archivo saneado como metadata (nunca usado para rutas), nombre de storage generado por UUID, proteccion ZIP-bomb (solo `.docx`, unico formato basado en ZIP: descomprime con contador acumulado, aborta sobre 100 MB o 2000 entradas sin confiar en el tamano declarado), hash SHA-256 para idempotencia real (`DocumentRepository.findByOwnerIdAndContentHash`, nuevo metodo del port), aislamiento estricto por `owner_id`.
  - Endpoints: `POST /api/documents`, `GET /api/documents`, `GET /api/documents/{id}`, `DELETE /api/documents/{id}` (`POST .../reindex` queda fuera, depende de embeddings reales). Documento queda en estado `UPLOADED`; el borrado limpia storage y fila (el `ON DELETE CASCADE` de `V7` ya cubre chunks/vectores futuros).
  - Diferido explicitamente (documentado, no implementado): proteccion XXE y "nunca ejecutar macros" (tareas de la futura extraccion, que es la que realmente parsea/abre contenido), antivirus (opcional segun spec), endpoint de descarga.
  - Pruebas: 13 unitarias (`DocumentManagementServiceTest`, incluye zip-bomb con un docx que infla a 101 MB desde bytes comprimidos), 5 del adaptador Tika, 4 de integracion HTTP end-to-end (`DocumentControllerIntegrationTest`: multipart + CSRF, 401/403, aislamiento por owner via HTTP), mas 2 nuevas en `FakeDocumentRepositoryTest`/`DocumentJpaAdapterIntegrationTest` para `findByOwnerIdAndContentHash`. `./mvnw verify` completo en verde (113/113).
- [ ] Extraccion, normalizacion y chunking (PDF, DOCX, TXT, Markdown, CSV, JSON) — sin aprobar todavia.
- [ ] Retrieval, citas, seleccion de documentos en el chat, panel `/knowledge` — sin aprobar todavia.

No se debe avanzar el resto del Sprint 5 (ni Sprint 6+) sin aprobacion explicita del propietario.

## Bugs corregidos (frontend, reportados 2026-07-16, cerrados 2026-07-16)

- [x] El boton de tema claro/oscuro solo cambiaba el icono; los colores de la UI se quedaban siempre en modo claro. Causa: `angular.json` cargaba el prebuilt theme estatico `azure-blue.css`, ajeno al `data-theme` de la app. Se reemplazo por `mat.theme()` (Material 3) con `theme-type: color-scheme`, que usa `light-dark()` y reacciona al `color-scheme` que la app ya alterna en `src/styles.scss`.
- [x] Faltaba un boton para eliminar conversaciones directamente desde la barra lateral. Se agrego un boton `✕`/`✓` (confirmacion en dos pasos) por fila en `frontend/src/app/features/chat/chat-page.html`, separado del boton de seleccion para no anidar `<button>`.
- [x] Al seleccionar un chat desde la barra lateral esta se contraia sola en escritorio. `openConversation()`/`newConversation()` ahora solo colapsan la barra cuando el viewport es movil (`matchMedia('(max-width: 860px)')`).
- [x] En movil la barra lateral no se contraia como overlay y el boton de "nuevo chat" no se veia. Causa: `.chat-shell` no tenia `position: relative`, asi que el `position: absolute` del drawer movil se anclaba al documento completo y quedaba tapado por la topbar `sticky`. Se agrego `position: relative` a `.chat-shell` y el estado inicial de `sidebarCollapsed` ahora depende del viewport.

Verificado en navegador real (Chromium vía Playwright) contra un stack Docker Compose aislado y desechable (proveedor FAKE, dos conversaciones, viewport de escritorio y movil 390×844): los 4 comportamientos quedaron confirmados visualmente, no solo por tipos/tests. Pruebas unitarias nuevas en `chat-page.spec.ts` cubren que el sidebar no se colapsa en escritorio al seleccionar otra conversacion y el flujo de borrado con confirmacion desde la fila.

## Bug corregido (frontend + infra, reportado y cerrado 2026-07-16): modo oscuro incompleto

- [x] El toggle de tema oscuro (ya arreglado arriba) solo recoloreaba botones y algunos textos de Material, pero fondos de tarjetas, badges y textos secundarios de `auth-page`, `admin-users-page`, `home-page` y `providers-page` seguian en claro. Causa 1: esas cuatro paginas usaban hex/rgba directos en vez de las variables `--surface-*`/`--text-*`/etc. de `src/styles.scss`; se migraron todas y se añadieron `--info-surface`/`--info-text` para los badges azules de "rol"/"fake". Causa 2 (mas grave, solo visible en el build de produccion via Docker, no en `ng serve`): Angular por defecto (`optimization.styles.inlineCritical`) inyecta un `<style>` critico inline con los valores de tema *claro* y difiere la hoja de estilos completa (con el override `html[data-theme='dark']`) con el truco `<link media="print" onload="this.media='all'">`; la CSP de nginx (`script-src 'self'`, sin `unsafe-inline`) bloquea ese `onload` inline, asi que la hoja completa se queda atrapada en `media="print"` y jamas se aplica en pantalla — solo los tokens `--mat-sys-*` de Material (que usan `light-dark()` nativo) cambiaban. Se desactivo `inlineCritical` en `frontend/angular.json` (configuracion `production`).
- [x] Hallazgo colateral relacionado, corregido en la misma pasada: recargar una ruta anidada (`/chat/<id>`, `/settings/providers`, etc.) rompia la carga de assets porque la CSP definia `base-uri 'none'`, bloqueando el `<base href="/">` de `index.html`. Se cambio a `base-uri 'self'` en `deployment/nginx/default.conf.template` (sigue bloqueando `<base>` apuntando a otro origen, que es el vector real de riesgo).

Verificado en navegador real contra el mismo tipo de stack Docker Compose aislado y desechable: home, proveedores (lista + conexion seleccionada + capacidades), MCP, login/registro y chat confirmados visualmente en oscuro completo (fondo casi negro, tarjetas oscuras, badges con la paleta correcta). `npm run format:check`, `lint`, `test:ci` (24/24) y `build` en verde.

## Proveedor añadido (2026-07-17): MiniMax

- [x] Nuevo `ProviderType.MINIMAX` con `MiniMaxProviderAdapter`: host fijo `https://api.minimax.io/v1` (verificado en la documentacion oficial), autenticacion bearer y Chat Completions compatible con OpenAI, incluido streaming SSE (`ProviderStreamingSupport.parseChatCompletions`, ya compartido con BytePlus). Sigue el mismo patron que BytePlus: no publica catalogo de modelos, asi que exige un model ID configurado (`CONFIGURED`) y una prueba de conexion acotada a un token de salida.
- [x] Wiring backend: bean en `ApplicationBeansConfiguration`, validacion de `configuredModelId` requerido en `ProviderManagementService`, pruebas de contrato (conexion, catalogo vacio y streaming) en `ProviderAdaptersTest` contra un servidor HTTP local — sin llamadas pagadas.
- [x] Frontend: tipo `MINIMAX` en `provider.models.ts`, tile en el selector de `providers-page.ts`/`.html` con el campo Model ID (sin base URL configurable, host fijo), copy actualizado en `home-page.html`.
- [x] Documentacion actualizada: `docs/providers.md`, `docs/chat.md`, `README.md`, `CHANGELOG.md`.
