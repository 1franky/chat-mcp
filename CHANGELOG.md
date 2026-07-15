# Changelog

Todos los cambios relevantes del proyecto se documentan aquí. El formato sigue Keep a Changelog y el proyecto aún no tiene una versión publicada.

## [Unreleased]

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

### Not implemented

- Identidad, usuarios, sesiones funcionales, chat, proveedores reales, MCP remoto y RAG.
