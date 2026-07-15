# AI Data Chat

Aplicacion de chat con IA orientada a datos, actualmente completada hasta Sprint 2. Este repositorio es un monorepo con backend Spring Boot, frontend Angular, PostgreSQL con pgvector y dobles de prueba deterministas. La especificacion principal es [`AI_DATA_CHAT_PROMPT.md`](AI_DATA_CHAT_PROMPT.md).

> Alcance actual: infraestructura, identidad, usuarios, conexiones LLM cifradas, prueba de proveedor, catalogo y seleccion predeterminada de modelos. No hay conversaciones, streaming de chat, RAG ni integracion remota con Data Platform MCP; esas capacidades pertenecen a sprints posteriores.

## Componentes

- `backend/`: monolito modular Java 21 con arquitectura hexagonal, seguridad, persistencia y adaptadores de proveedor tras puertos propios.
- `frontend/`: aplicacion Angular en espanol con identidad, administracion y configuracion de proveedores/modelos.
- `deployment/nginx/`: hosting estático, proxy de `/api`, configuración preparada para SSE y headers de seguridad.
- `test-support/fake-mcp/`: servidor contractual WireMock con únicamente `health_check` y `hello_world`.
- `docs/`: arquitectura, seguridad, integraciones y ADRs.

```mermaid
flowchart LR
    U["Navegador"] -->|":3000, red chat-ingress"| F["chat-frontend / Nginx"]
    F -->|"/api, red chat-internal"| B["chat-backend"]
    B -->|"JDBC, red chat-internal"| P["chat-postgres + pgvector"]
    B -.->|"Sprint futuro, red ai-platform"| M["Data Platform MCP"]
    FM["fake-mcp, sólo compose.dev"] -.->|"contrato de prueba"| B
```

`chat-frontend` es el único servicio en `chat-ingress`; sólo `chat-backend` pertenece a `chat-internal` y a la red externa `ai-platform`. PostgreSQL no publica puertos al host.

## Requisitos

- Docker Engine 27+ con Compose v2.
- Para desarrollo sin contenedores: JDK 21–26, Maven Wrapper y Node `^22.22.3`, `^24.15.0` o `^26.0.0`.
- La red Docker externa `ai-platform`.

Las versiones seleccionadas y sus fuentes oficiales están en [`docs/versions.md`](docs/versions.md).

## Arranque

```bash
cp .env.example .env
# Sustituye POSTGRES_PASSWORD y genera CREDENTIAL_MASTER_KEY.
# openssl rand -base64 32
./scripts/ensure-network.sh
docker compose up --build --wait
```

La UI queda en <http://localhost:3000>. Comprobaciones rápidas:

```bash
curl --fail http://localhost:3000/healthz
curl --fail http://localhost:3000/api/system/status
docker compose ps
```

Al entrar por primera vez, la UI solicita crear la cuenta inicial. La base de datos asigna `ADMIN`
atomicamente a una sola cuenta, incluso ante registros concurrentes. Los registros posteriores son
`USER`. Para cerrar el registro publico despues del bootstrap:

```dotenv
ALLOW_PUBLIC_REGISTRATION=false
```

Para añadir el MCP contractual de pruebas:

```bash
docker compose -f compose.yaml -f compose.dev.yaml up --build --wait
```

El MCP continua en modo fake. Para LLM, la UI permite configurar el fake determinista o conexiones
OpenAI, Anthropic, BytePlus, OpenAI-compatible y Ollama. Ninguna prueba automatizada consume APIs
pagadas; probar una conexion real sólo ocurre por accion explícita del usuario.

## Proveedores y modelos

Abre <http://localhost:3000/settings/providers> después de iniciar sesion. Cada conexion pertenece
al usuario autenticado. Las claves se cifran con AES-256-GCM y la API sólo devuelve una pista
enmascarada.

OpenAI-compatible y Ollama necesitan una allowlist del operador. Ejemplo para Ollama en la red
Docker:

```dotenv
PROVIDER_ALLOWED_HOSTS=ollama
PROVIDER_ALLOWED_HTTP_HOSTS=ollama
```

Con allowlist vacía se bloquean todos los destinos configurables. BytePlus admite actualmente la
region oficial documentada `ap-southeast-1` y requiere un model ID o endpoint ID configurado.

Para detener el entorno sin borrar datos:

```bash
docker compose down
```

## Calidad y pruebas

Backend:

```bash
cd backend
./mvnw -B -ntp verify
```

Incluye pruebas unitarias de cifrado, SSRF, fakes y contratos HTTP, reglas ArchUnit y pruebas
Testcontainers sobre PostgreSQL/pgvector real para migraciones, identidad, concurrencia y ownership
de proveedores.

Frontend:

```bash
cd frontend
npm ci
npm run format:check
npm run lint
npm run test:ci
npm run build
```

Las pruebas de navegador de registro inicial y login se ejecutan con `npm run e2e`; requieren instalar Chromium mediante Playwright. La integracion continua replica formato, lint, pruebas, builds y auditoria de dependencias con severidad alta.

## Configuración

`.env.example` contiene sólo marcadores. `CREDENTIAL_MASTER_KEY` es obligatoria, debe ser base64 de
32 bytes y no debe persistirse en Git. `CREDENTIAL_KEY_VERSION` queda almacenada con cada secreto.

`APP_INTEGRATIONS_MODE=fake` conserva el proveedor y MCP deterministas. Las credenciales reales sólo
se usan cuando su propietario solicita una prueba o sincronizacion desde la UI.

En produccion deben configurarse `SESSION_COOKIE_SECURE=true` y `CSRF_COOKIE_SECURE=true`, servir
la aplicacion exclusivamente por HTTPS y conservar el proxy mismo-origen. Consulta
[Identidad](docs/identity.md) y [Seguridad](docs/security.md).

## Documentación

- [Arquitectura](docs/architecture.md)
- [Seguridad](docs/security.md)
- [Identidad y API](docs/identity.md)
- [Proveedores](docs/providers.md)
- [Integración MCP](docs/mcp-integration.md)
- [RAG](docs/rag.md)
- [Decisiones ADR](docs/adr/README.md)
- [Tareas](TASKS.md)
- [Cambios](CHANGELOG.md)

## Estado del roadmap

Sprint 0, Sprint 1 y Sprint 2 estan implementados. Sprint 3 (conversaciones, mensajes y streaming)
no debe comenzar sin aprobacion explicita del propietario del proyecto.
