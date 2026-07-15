# Tareas

Actualizado: 2026-07-15.

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

## Fuera del Sprint 2

- [ ] Sprint 3 — conversaciones, mensajes, streaming, cancelacion y UI completa de chat. Requiere aprobacion explicita.
- [ ] Añadir en Sprint 3 un boton accesible para alternar entre el tema claro existente y un nuevo tema oscuro, con preferencia persistente y respeto inicial de `prefers-color-scheme`.
- [ ] Sprint 4 — implementar el cliente MCP Streamable HTTP, discovery de tools, tool calling multi-proveedor, tarjetas de tools y pruebas de contrato/seguridad.
- [ ] Configurar en Sprint 4 la conexion backend-a-backend con Data Platform MCP mediante `MCP_BASE_URL`, `MCP_ENDPOINT`, version de protocolo y major de contrato, conservando el servicio fuera del navegador y sin modificar el proyecto MCP.
- [ ] Mostrar en Sprint 4 el estado `UP`/`DEGRADED`/`DOWN`, el contrato negociado y las funciones disponibles del MCP en un panel de solo lectura para el usuario.
- [ ] Sprint 5+ — RAG y demas roadmap definido en la especificacion.

No se debe iniciar ninguna tarea fuera de Sprint 2 sin aprobacion del propietario.
