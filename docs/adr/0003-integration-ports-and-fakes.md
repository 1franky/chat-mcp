# ADR-0003: Integraciones sólo mediante puertos y fakes

- Estado: Accepted
- Fecha: 2026-07-15

## Contexto

Las pruebas no deben llamar APIs pagadas y Data Platform MCP evoluciona fuera de este repositorio.

## Decisión

Representar LLM, catálogo, MCP, embeddings, almacenamiento y búsqueda con puertos propios. En Sprint 0 cablear únicamente adaptadores fake deterministas y limitar el contrato MCP a `health_check` y `hello_world`. Mantener `APP_INTEGRATIONS_MODE=fake` en Compose.

## Consecuencias

El bootstrap funciona offline respecto de proveedores y no necesita secretos. Los fakes son explícitos y no se presentan como integración real. Habilitar un adaptador remoto exigirá otro ADR o una ampliación aprobada del sprint correspondiente.
