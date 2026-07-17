# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

AI Data Chat: a data-oriented AI chat application. This is a monorepo with a Spring Boot backend
(Java 21, hexagonal architecture), an Angular frontend (in Spanish), PostgreSQL + pgvector, and
deterministic test doubles for LLM/MCP integrations. The full spec lives in
`AI_DATA_CHAT_PROMPT.md`; roadmap status lives in `TASKS.md`.

**Current scope (through Sprint 4):** infrastructure, identity, users, encrypted LLM provider
connections, persistent chat with streaming/cancellation, a real MCP Streamable HTTP client, and
backend-orchestrated tool calling for OpenAI and Anthropic only. **Sprint 5 (RAG) work beyond what
the project owner has explicitly approved must not be started** — see `TASKS.md` for exactly which
Sprint 5 pieces are approved and landed so far (as of 2026-07-17: the `rag` Flyway schema and
`EmbeddingProviderPort` with its fake adapter only; upload, extraction, chunking, retrieval,
citations and the `/knowledge` UI are still unapproved).

## Commands

### Backend (`backend/`)

```bash
cd backend
./mvnw -B -ntp verify        # full build: compile, tests, ArchUnit, Checkstyle, Spotless
./mvnw test -Dtest=ChatServiceTest             # run a single test class
./mvnw test -Dtest=ChatServiceTest#methodName  # run a single test method
./mvnw spotless:apply         # auto-format Java (Google Java Format, AOSP style)
```

Testcontainers spins up real PostgreSQL/pgvector for integration tests (migrations, identity
concurrency, provider/conversation ownership, usage persistence, partial cancellation) — Docker
must be available locally. Checkstyle and Spotless both run in the `verify` phase and fail the
build on violation; there is no separate lint step.

### Frontend (`frontend/`)

```bash
cd frontend
npm ci
npm run format:check     # prettier --check
npm run lint             # ng lint (ESLint + angular-eslint)
npm run test:ci          # ng test --watch=false (Vitest)
npm run build            # ng build
npm run e2e               # Playwright, needs: npx playwright install chromium
```

Run a single Vitest spec via the Angular CLI test runner filter, e.g.
`npx ng test --watch=false -- --testNamePattern=<name>`, or just target the spec file directly with
vitest if working outside `ng test`. E2E specs live in `frontend/e2e/*.spec.ts` and drive a real
browser against `ng serve` on port 4200.

### Full stack (Docker Compose)

```bash
cp .env.example .env      # set POSTGRES_PASSWORD and CREDENTIAL_MASTER_KEY (openssl rand -base64 32)
./scripts/ensure-network.sh   # creates the external `ai-platform` Docker network
docker compose up --build --wait
# add the fake-mcp WireMock contract double:
docker compose -f compose.yaml -f compose.dev.yaml up --build --wait
```

UI at `http://localhost:3000`. `docker compose down` stops without deleting data. CI
(`.github/workflows/ci.yml`) runs backend `verify`, frontend format/lint/test/build/`npm audit
--audit-level=high`, then multi-arch (amd64/arm64) container builds — mirror these locally before
pushing.

## Architecture

### Backend: hexagonal / ports-and-adapters

Package root: `com.aidatachat`. Layering, enforced by ArchUnit (`HexagonalArchitectureTest`):

```
web  →  application.port.in  →  application.service  →  domain.model
                                        ↓
                                application.port.out  ←  adapters.out.* (persistence, security, provider, fake)
configuration → wires application.service to concrete adapters
```

- `domain` depends on nothing else in the codebase (no Spring, no adapters, no web).
- `application` depends only on `domain` and its own ports — never on adapters, infrastructure, or web.
- Every external system (LLM providers, MCP, embeddings, document storage, vector search,
  credential cipher, conversation/document/audit persistence) is reached exclusively through a
  port interface: `LlmProviderPort`, `ModelCatalogPort`, `McpGateway`, `EmbeddingProviderPort`,
  `DocumentStoragePort`, `VectorSearchPort`, `CredentialCipherPort`, `ConversationRepository`,
  `DocumentRepository`, `AuditRepository`.
- `adapters.out.fake` provides deterministic, in-process, no-network implementations of these
  ports for tests and for `APP_INTEGRATIONS_MODE=fake`. Real adapters (JPA persistence,
  Argon2/session security, AES-256-GCM cipher, HTTP provider clients) live under `adapters.out.*`
  and are swapped in by `configuration`.
- Flyway is the sole schema authority (`ddl-auto=validate`); migrations live in
  `backend/src/main/resources/db/migration`. Schemas: `identity`, `audit`, provider tables, and
  `chat.conversation`/`chat.message`. The `rag` schema now has `document`, `document_chunk`
  (`vector(1536)` + HNSW index) and `message_document` (`V7`), but no adapter writes to it yet — see
  `docs/rag.md` for exactly what is and isn't approved.
- Adding or changing an integration (LLM provider, MCP transport, etc.) means adding an adapter
  behind the existing port — never reaching into `application`/`domain` from adapter code, and
  never adding a new external dependency directly into `application` or `domain`.

### Frontend: standalone Angular, Spanish UI

`frontend/src/app/core/*` holds cross-cutting concerns (auth, chat, http interceptors, provider
client, theme). `frontend/src/app/features/*` holds routed feature areas (admin-users, auth, chat,
home, providers). The UI text is in Spanish; keep new UI copy in Spanish for consistency.

The app never stores JWTs, cookies, passwords, or secrets in `localStorage`/`sessionStorage` — only
the light/dark theme preference is persisted locally. Session state is server-side (Spring Session
JDBC); the browser only holds an opaque session cookie plus a CSRF cookie/header pair.

### Security model (read `docs/security.md` before touching auth, providers, or chat persistence)

- Argon2id password hashing; Spring Session JDBC backs `AI_DATA_CHAT_SESSION`
  (`HttpOnly`, `SameSite=Lax`, `Secure` when `SESSION_COOKIE_SECURE=true`).
- CSRF via `XSRF-TOKEN` cookie / `X-XSRF-TOKEN` header (SPA pattern); required on all writes.
- The first user to register in an empty database becomes `ADMIN` (via a `SERIALIZABLE`
  transaction + Postgres advisory lock, retried up to 3 times on serialization abort); every
  subsequent registration or admin-created user is `USER`. The client never sends a role. The last
  active admin can never be demoted or deactivated.
- Provider credentials are encrypted with AES-256-GCM (random 96-bit nonce, 128-bit tag, versioned
  AAD, `CREDENTIAL_KEY_VERSION`). `CREDENTIAL_MASTER_KEY` must decode to exactly 32 bytes; the
  backend refuses to start without a valid one. API responses only ever include a 4-character
  masked hint — never ciphertext, nonce, or key.
- All provider and conversation reads/writes filter by owner ID *and* resource ID together, even
  for `ADMIN` — admin role grants user management only, never access to other users' private data.
- OpenAI-compatible and Ollama provider connections require an exact-host allowlist
  (`PROVIDER_ALLOWED_HOSTS`, `PROVIDER_ALLOWED_HTTP_HOSTS` for plain HTTP); redirects,
  link-local/metadata addresses, and URLs with userinfo/query/fragment are rejected as SSRF
  vectors. An empty allowlist blocks all configurable destinations.
- Chat prompts, deltas, and provider response bodies are never written to logs or audit records;
  only bounded metadata (request IDs, finish reasons) is persisted.
- Markdown rendering escapes HTML/attributes and allows only `http`/`https`/`mailto` links; Angular
  re-sanitizes before binding to `innerHTML`.

### Chat / streaming

`ChatUseCase` (application) owns ownership checks, history windowing, state transitions, and
audit; JPA adapters own message ordering/concurrency (position assignment under a per-conversation
lock, a partial index enforcing at most one `STREAMING` assistant message per conversation);
provider adapters translate each provider's stream format into an internal `LlmChunk`. SSE
(`SseEmitter`) is same-origin only, requires session + CSRF, disables Nginx buffering for `/api`,
sends heartbeats, and cancels the upstream provider call if the browser disconnects — a
disconnected/stopped generation is persisted as `CANCELLED`, not lost.

Each assistant message stores an immutable snapshot of the provider connection, model ID, usage,
`finishReason`, and request ID at generation time; changing a conversation's selected
provider/model never rewrites past messages. Supported provider stream formats: OpenAI Responses
SSE, Anthropic Messages SSE, BytePlus/OpenAI-compatible Chat Completions SSE, Ollama NDJSON, and an
in-process deterministic `FAKE` provider (`fake-chat-v1`) for cost-free local testing. See
`docs/chat.md` and `docs/providers.md` for the full API and per-provider details.

### MCP integration — fake only, do not build a real client without approval

`McpGateway` is the only MCP contract `application` knows about. Two doubles exist:
`FakeMcpGateway` (in-process) and a WireMock server under `test-support/fake-mcp/` (HTTP/JSON-RPC
contract testing). Both expose only `health_check` and `hello_world` — no database/SQL/report
capabilities are invented. A real Streamable HTTP client against Data Platform MCP is explicitly
out of scope until Sprint 4 is approved (`docs/mcp-integration.md`, `docs/adr/0003-*`).

### Docker network topology

`chat-frontend` (Nginx) is the only service on `chat-ingress` and the sole HTTP entry point.
`chat-backend` is the only service that bridges the internal `chat-internal` network and the
external `ai-platform` network (where a real or fake MCP would live). PostgreSQL is internal-only
and publishes no host port. `compose.dev.yaml` adds `fake-mcp` to `ai-platform` without touching
Data Platform MCP itself. See `docs/architecture.md` for diagrams.

## Testing philosophy

No automated test may call a paid external API. All LLM/MCP integration tests run against
in-process fakes or local HTTP servers (WireMock for MCP; local HTTP servers for provider
contract tests). Real provider connections are only exercised by explicit, user-initiated action
in the running app (e.g., clicking "test connection"), never in CI or test suites. Backend
integration tests use Testcontainers against real PostgreSQL/pgvector rather than mocking the
database.

## Documentation map

Architecture, security, identity, providers, chat, MCP, and RAG each have a dedicated doc under
`docs/`; architectural decisions are recorded as ADRs under `docs/adr/`. Consult the relevant one
before changing behavior in that area rather than re-deriving conventions from code alone —
several of the constraints above (SSRF allowlisting, credential encryption, single-active-admin
invariant, single-active-generation invariant) are easy to accidentally violate with a
locally-reasonable-looking change.
