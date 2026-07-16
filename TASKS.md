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

## Fuera del Sprint 4

- [ ] Sprint 5+ — RAG y demas roadmap definido en la especificacion.

No se debe iniciar ninguna tarea fuera del Sprint 4 sin aprobacion del propietario.
