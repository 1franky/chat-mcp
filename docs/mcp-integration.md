# Integración MCP

## Frontera

`McpGateway` es el único contrato que `application` conoce. La versión estable seleccionada del
protocolo es `2025-11-25`. Desde Sprint 4 existe un cliente Streamable HTTP real
(`adapters/out/mcp/`) además de los dobles deterministas; ver ADR-0009 para el razonamiento
completo de diseño.

## Adaptadores disponibles

Dos implementaciones de `McpGateway`, seleccionadas por la property `app.mcp.mode`
(`MCP_INTEGRATION_MODE`), independiente del flag `app.integrations.mode` que gobierna el proveedor
LLM `FAKE`:

1. `FakeMcpGateway` en proceso (`app.mcp.mode=fake`, valor por defecto) — sin red, para pruebas
   unitarias y el modo de desarrollo sin dependencias externas.
2. `RealMcpGateway` (`app.mcp.mode=real`) — cliente Streamable HTTP real en
   `adapters/out/mcp/McpSessionManager.java`: negocia `initialize` → `notifications/initialized` →
   `tools/list`, cachea el catálogo de tools, valida el major de `contract_version` contra
   `MCP_CONTRACT_MAJOR` y expone un `status()` que nunca lanza (`UP`/`DEGRADED`/`DOWN`), refrescado
   periódicamente por `mcpStatusRefreshScheduler`.

El doble WireMock en `test-support/fake-mcp` sigue existiendo para pruebas de contrato HTTP/JSON-RPC
aisladas y para ejercitar `RealMcpGateway` en `compose.dev.yaml` sin tocar Data Platform MCP.

Ambos dobles publican únicamente las tools que la especificación confirma para el fake:

- `health_check`
- `hello_world`

El cliente real descubre dinámicamente el catálogo real que exponga Data Platform MCP vía
`tools/list`; el backend nunca inventa ni asume tools que el servidor no reporte. `tools/call`
rechaza por contrato cualquier nombre no permitido, tanto en los dobles como en la orquestación de
`ChatService` (allowlist contra el catálogo descubierto).

## Orquestación de tool calling

El backend es siempre el único cliente/orquestador MCP — nunca delega la conexión al proveedor LLM
aunque éste soporte "remote MCP" (ver ADR-0009). `ChatService` ofrece el catálogo de tools
descubierto a cualquier proveedor activo; en la práctica solo lo aprovechan los proveedores cuyo
adaptador serializa `tools`/`tool_calls` en el payload — OpenAI, Anthropic, MiniMax, BytePlus y
OpenAI-compatible en su variante Chat Completions (`toolCalling=SUPPORTED` en su
`ProviderCapabilityProfile`; ver `docs/chat.md`). Ollama ignora `tools` en su body actual y no
invoca herramientas. El backend ejecuta cada llamada en un executor dedicado
(`mcpToolOrchestrationExecutor`, nunca en el hilo
Reactor Netty del stream), aplica timeout por-tool (`MCP_TOOL_CALL_TIMEOUT`) y límite de tamaño de
resultado (`MAX_TOOL_RESULT_BYTES`), y detiene la generación en `FAILED` si se supera
`MAX_TOOL_ROUNDS`. Un resultado `isError:true` o bloqueado por el allowlist nunca se presenta como
éxito. Las tool calls se persisten en `chat.message_tool_call` (migración `V5`), colgando de la
misma fila `ASSISTANT` de la generación.

## Compatibilidad

El fake responde a `initialize`, notificación `notifications/initialized`, `tools/list` y
`tools/call`. Declara contrato de fixture `1.0.0`; no afirma ser una implementación completa del SDK
o del servidor real.

Variables reservadas:

- `MCP_INTEGRATION_MODE=fake` — `fake` (por defecto) o `real`.
- `MCP_BASE_URL=http://data-platform-mcp:8000`
- `MCP_ENDPOINT=/mcp`
- `MCP_PROTOCOL_VERSION=2025-11-25`
- `MCP_CONTRACT_MAJOR=1`
- `MCP_STATUS_REFRESH_INTERVAL=30s`
- `MCP_TOOL_CALL_TIMEOUT=20s`
- `MCP_HTTP_TIMEOUT=10s`
- `MCP_HTTP_MAX_RESPONSE_BYTES=1048576`
- `MAX_TOOL_ROUNDS=6`
- `MAX_TOOL_RESULT_BYTES=1048576`

## Red y propiedad

Sólo `chat-backend` puede alcanzar `ai-platform`. El overlay de desarrollo coloca el fake en esa red
sin publicar puerto y activa `MCP_INTEGRATION_MODE=real` para ejercitar el cliente real contra ese
mismo doble WireMock. Este repositorio no modifica ni administra Data Platform MCP.

## Autenticación

Data Platform MCP no tiene autenticación hoy. `McpAuthProvider` (`NoOpMcpAuthProvider` es la única
implementación) es un punto de extensión explícito para bearer token, OAuth de servicio o mTLS
futuros, sin cambiar el contrato de `McpGateway`.

## Panel de estado

`GET /api/mcp/status` y `GET /api/mcp/tools` alimentan el panel de solo lectura en
`/settings/mcp` (ADMIN y USER, sin restricción de rol): estado `UP`/`DEGRADED`/`DOWN`, versión del
servidor, contrato y protocolo negociados, y el catálogo de tools disponibles con su indicador de
solo-lectura.
