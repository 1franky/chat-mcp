# Arquitectura

## Contexto y alcance

Sprint 4 mantiene un monolito modular desplegable. Los limites internos permiten cambiar persistencia e integraciones sin acoplar dominio y casos de uso a Spring, SDKs de proveedores o MCP.

```mermaid
flowchart TB
    WEB["web: controladores y Problem Details"] --> IN["application.port.in"]
    IN --> APP["application.service"]
    APP --> DOMAIN["domain.model"]
    APP --> OUT["application.port.out"]
    CFG["configuration: composicion y seguridad"] --> APP
    CFG --> FAKE["adapters.out.fake"]
    FAKE --> OUT
    JPA["adapters.out.persistence: JPA/JDBC"] --> OUT
    SEC["adapters.out.security: Argon2/sesiones"] --> OUT
    PROVIDERS["adapters.out.provider: HTTP y SSRF"] --> OUT
    MCP["adapters.out.mcp: cliente Streamable HTTP"] --> OUT
    CHAT["adapters.out.persistence.chat: conversaciones y tool calls"] --> OUT
    CIPHER["adapters.out.security: AES-256-GCM"] --> OUT
```

Reglas verificadas con ArchUnit:

- `domain` no depende de `application`, `adapters`, `configuration`, `infrastructure` ni `web`.
- `application` no depende de adaptadores, infraestructura o web.
- Los contratos externos se expresan mediante puertos propios.

## Puertos preparados

`LlmProviderPort`, `ModelCatalogPort`, `McpGateway`, `EmbeddingProviderPort`, `DocumentStoragePort`, `VectorSearchPort`, `CredentialCipherPort`, `ConversationRepository`, `DocumentRepository` y `AuditRepository`.

Sprint 1 anadio puertos de identidad y sesiones. Sprint 2 activo proveedores y cifrado. Sprint 3
activa `ChatUseCase`, `ConversationRepository` y `LlmChatGateway`: el caso de uso controla ownership,
historial, estados y auditoria; JPA controla orden/concurrencia; los adaptadores traducen streams
externos a `LlmChunk`. Sprint 4 anade `adapters/out/mcp/` (cliente Streamable HTTP real,
seleccionable junto al fake vía `app.mcp.mode`) y la orquestacion de tool calling en `ChatService`
para OpenAI y Anthropic (ver ADR-0009); documentos y vectores siguen sin capacidad funcional.

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

Flyway crea la extension `vector`, namespaces delimitados, identidad, sesiones, auditoria,
proveedores y `chat.conversation`/`chat.message`. El schema `rag` sigue vacio. JPA usa
`ddl-auto=validate`; Flyway es la unica autoridad de esquema. Un indice parcial impide dos mensajes
de asistente `STREAMING` para la misma conversacion y un lock pesimista asigna posiciones estables.

## Flujo disponible

```mermaid
sequenceDiagram
    participant UI as Angular
    participant WEB as AuthController
    participant APP as IdentityService
    participant DB as PostgreSQL
    UI->>WEB: GET /api/auth/bootstrap
    WEB->>APP: bootstrap(principal)
    APP->>DB: existe algun usuario?
    DB-->>APP: no
    UI->>WEB: POST /api/auth/register + CSRF
    WEB->>APP: register(command)
    APP->>DB: SERIALIZABLE + advisory lock + INSERT ADMIN
    WEB->>DB: guardar sesion JDBC
    WEB-->>UI: perfil publico + cookie HttpOnly
```

Tambien estan disponibles login/logout, administracion y proveedores propios. En chat, Spring MVC
expone `SseEmitter`; un publisher de aplicacion persiste cada parcial, normaliza eventos terminales y
cancela el upstream si el navegador desaparece. Cada mensaje del asistente conserva el snapshot con
el que se genero. Las pruebas usan dobles y servidores locales; ninguna invoca APIs pagadas.

```mermaid
sequenceDiagram
    participant UI as Angular
    participant API as ChatController / SSE
    participant APP as ChatService
    participant DB as PostgreSQL
    participant LLM as LlmChatGateway
    UI->>API: POST messages/stream + CSRF
    API->>APP: startGeneration(owner, conversation, content)
    APP->>DB: USER COMPLETED + ASSISTANT STREAMING
    APP->>LLM: historial acotado + modelo seleccionado
    loop deltas
      LLM-->>APP: LlmChunk
      APP->>DB: persistir parcial
      APP-->>UI: event: delta
    end
    APP->>DB: COMPLETED / CANCELLED / FAILED
    APP-->>UI: evento terminal + uso
```

Para OpenAI y Anthropic, si el modelo pide una tool, `ChatService` no delega la conexion MCP al
proveedor: valida el nombre contra el catalogo descubierto, ejecuta la llamada en un executor
dedicado y reinyecta el resultado como un nuevo turno antes de continuar el mismo stream.

```mermaid
sequenceDiagram
    participant LLM as LlmChatGateway
    participant APP as ChatService
    participant DB as PostgreSQL
    participant MCP as McpGateway
    LLM-->>APP: LlmChunk con tool_calls / tool_use
    APP->>DB: message_tool_call PENDING
    APP-->>UI: event: tool_call
    APP->>MCP: call(toolName, arguments) [executor dedicado]
    MCP-->>APP: McpToolResult (isError nunca se reescribe)
    APP->>DB: message_tool_call COMPLETED/FAILED/BLOCKED
    APP-->>UI: event: tool_result
    APP->>LLM: historial + resultado de la tool
```
