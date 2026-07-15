# Arquitectura

## Contexto y alcance

Sprint 0 entrega un monolito modular desplegable, no microservicios funcionales. Los límites internos permiten cambiar adaptadores sin acoplar dominio y casos de uso a Spring AI, SDKs de proveedores o MCP.

```mermaid
flowchart TB
    WEB["web: controladores y Problem Details"] --> IN["application.port.in"]
    IN --> APP["application.service"]
    APP --> DOMAIN["domain.model"]
    APP --> OUT["application.port.out"]
    CFG["configuration: composición"] --> APP
    CFG --> FAKE["adapters.out.fake"]
    FAKE --> OUT
    INFRA["infrastructure: persistencia futura"] --> OUT
```

Reglas verificadas con ArchUnit:

- `domain` no depende de `application`, `adapters`, `configuration`, `infrastructure` ni `web`.
- `application` no depende de adaptadores, infraestructura o web.
- Los contratos externos se expresan mediante puertos propios.

## Puertos preparados

`LlmProviderPort`, `ModelCatalogPort`, `McpGateway`, `EmbeddingProviderPort`, `DocumentStoragePort`, `VectorSearchPort`, `CredentialCipherPort`, `ConversationRepository`, `DocumentRepository` y `AuditRepository`.

Sólo `LlmProviderPort`, `ModelCatalogPort` y `McpGateway` tienen adaptadores fake en Sprint 0. El resto es una frontera de compilación, no una capacidad implementada.

## Contenedores y redes

```mermaid
flowchart LR
    subgraph INGRESS["chat-ingress"]
      F["chat-frontend"]
    end
    subgraph INTERNAL["chat-internal (internal=true)"]
      B["chat-backend"]
      B --> P["chat-postgres"]
    end
    subgraph PLATFORM["ai-platform (external)"]
      M["data-platform-mcp / fake-mcp"]
    end
    F --> B
    B -.-> M
```

- Nginx es la única entrada HTTP, pertenece a `chat-ingress` y mantiene un mismo origen.
- PostgreSQL se limita a la red interna y a un volumen nombrado.
- Sólo backend conecta `chat-internal` con la red externa `ai-platform`.
- `compose.dev.yaml` agrega `fake-mcp` a `ai-platform`; no modifica Data Platform MCP.

## Datos

Flyway crea la extensión `vector` y los namespaces `identity`, `chat`, `rag` y `audit`. No crea tablas funcionales porque corresponderían a sprints posteriores. JPA usa `ddl-auto=validate`; Flyway es la única autoridad de esquema.

## Flujo disponible

El único flujo web de producto es `GET /api/system/status`: el controlador llama al caso de uso, que consulta los puertos fake y devuelve su estado declarado. No transmite chats ni llama servicios externos.
