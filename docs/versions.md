# Versiones seleccionadas

Verificación realizada el 2026-07-15 contra documentación o registros oficiales.

| Tecnología | Estable actual verificada | Selección de Sprint 0 | Motivo |
| --- | --- | --- | --- |
| Java | 26.0.1 | 21.0.11, bytecode 21 | La especificación exige Java 21; es una versión LTS y Spring Boot 4.1 la soporta. |
| Spring Boot | 4.1.0 | 4.1.0 | Última estable y compatible con Java 21. |
| Spring AI | 2.0.0 | BOM 2.0.0 | Compatible con Spring Boot 4.0/4.1; no se activa un proveedor real en Sprint 0. |
| Angular | 22.0.6 | 22.0.6 | Rama activa estable al verificar; Angular Material/CDK quedan en 22.0.4, su parche publicado compatible. |
| PostgreSQL | 18.4 | 18.4 | Último minor soportado de la rama actual. |
| pgvector | 0.8.2 | 0.8.2 | Último release estable. |
| MCP | 2025-11-25 | 2025-11-25 | Versión estable actual del protocolo; 2026-07-28 era release candidate y no se seleccionó. |

Fuentes:

- [Descargas de Java SE (Oracle)](https://www.oracle.com/java/technologies/downloads/)
- [Requisitos de sistema de Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html)
- [Getting Started de Spring AI](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Calendario de versiones Angular](https://angular.dev/reference/releases)
- [Política y versiones PostgreSQL](https://www.postgresql.org/support/versioning/)
- [Releases de pgvector](https://github.com/pgvector/pgvector/releases)
- [Versionado del protocolo MCP](https://modelcontextprotocol.io/docs/learn/versioning)

## Imágenes

Las imágenes de ejecución están fijadas por versión y digest y sus manifiestos incluyen `linux/arm64`: Eclipse Temurin 21.0.11_10, Node 24.15.0 Alpine, Nginx unprivileged 1.28.0 Alpine, pgvector 0.8.2 sobre PostgreSQL 18 y WireMock 3.13.2.

Maven Wrapper queda fijado en Maven 3.9.16. El rango del Enforcer permite ejecutar con JDK 21 a 26, pero el compilador siempre produce bytecode Java 21.
