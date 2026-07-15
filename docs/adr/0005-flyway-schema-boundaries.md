# ADR-0005: Flyway y namespaces acotados

- Estado: Accepted
- Fecha: 2026-07-15

## Contexto

JPA no debe alterar silenciosamente producción y los sprints futuros necesitan ownership claro de datos.

## Decisión

Hacer de Flyway la única autoridad de DDL, con `ddl-auto=validate`. La primera migración habilita `vector` y crea `identity`, `chat`, `rag` y `audit`, sin tablas funcionales antes de sus sprints.

## Consecuencias

Las migraciones son reproducibles y verificables con Testcontainers. Cada capacidad deberá añadir DDL versionado en su sprint; los namespaces no significan que la funcionalidad ya exista.
