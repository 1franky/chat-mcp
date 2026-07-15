# Arquitectura

## Contexto y alcance

Sprint 2 mantiene un monolito modular desplegable. Los limites internos permiten cambiar persistencia e integraciones sin acoplar dominio y casos de uso a Spring, SDKs de proveedores o MCP.

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
    CIPHER["adapters.out.security: AES-256-GCM"] --> OUT
```

Reglas verificadas con ArchUnit:

- `domain` no depende de `application`, `adapters`, `configuration`, `infrastructure` ni `web`.
- `application` no depende de adaptadores, infraestructura o web.
- Los contratos externos se expresan mediante puertos propios.

## Puertos preparados

`LlmProviderPort`, `ModelCatalogPort`, `McpGateway`, `EmbeddingProviderPort`, `DocumentStoragePort`, `VectorSearchPort`, `CredentialCipherPort`, `ConversationRepository`, `DocumentRepository` y `AuditRepository`.

Sprint 1 anadio `UserAccountRepository`, `IdentityTransactionPort`, `PasswordHashPort` y
`SessionInvalidationPort`. Sprint 2 activa `LlmProviderPort`, `CredentialCipherPort` y
`ProviderConnectionRepository` con adaptadores OpenAI, Anthropic, BytePlus, OpenAI-compatible,
Ollama y fake. MCP conserva exclusivamente el adaptador fake; chat, documentos y vectores siguen
sin capacidad funcional.

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

Flyway crea la extension `vector`, namespaces delimitados, identidad, sesiones, auditoria y las
tablas `chat.provider_connection` y `chat.provider_model`. El schema `rag` sigue vacio. JPA usa
`ddl-auto=validate`; Flyway es la unica autoridad de esquema.

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

Tambien estan disponibles login/logout, perfil, administracion de usuarios y gestion de conexiones
y modelos propios. El endpoint de estado se conserva. Ningun flujo transmite chats; las pruebas
usan dobles y servidores locales, y una prueba real sólo ocurre por accion explícita del usuario.
