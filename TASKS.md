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

## Fuera del Sprint 1

- [ ] Sprint 2 — proveedores reales, catalogo y cifrado de credenciales. Requiere aprobacion explicita.
- [ ] Sprint 3+ — chat, tools remotas, RAG y demás roadmap definido en la especificación.

No se debe iniciar ninguna tarea fuera de Sprint 1 sin aprobacion del propietario.
