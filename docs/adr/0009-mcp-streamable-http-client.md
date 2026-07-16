# ADR-0009 — Cliente MCP Streamable HTTP y orquestacion de tool calling

- Estado: aceptado
- Fecha: 2026-07-16

## Contexto

ADR-0003 limito MCP a dobles deterministas (`FakeMcpGateway` y el contrato WireMock) y exigio "otro
ADR o una ampliacion aprobada del sprint correspondiente" antes de habilitar un adaptador remoto.
`docs/mcp-integration.md` anticipaba que ese adaptador "debera usar Spring AI o el SDK oficial Java"
detras de `McpGateway`. El propietario del proyecto aprobo explicitamente el Sprint 4 (cliente MCP
real, discovery de tools, tool calling multi-proveedor, tarjetas de tools, pruebas de
contrato/seguridad), sustituyendo esa expectativa inicial por la decision descrita aqui.

## Decision

**Cliente hecho a mano, no Spring AI ni el SDK oficial de MCP.** `adapters/out/mcp/` replica el
patron ya validado en `adapters/out/provider/` (WebClient sobre Reactor Netty, politica de destino
SSRF propia, parsing JSON-RPC manual con Jackson) en vez de introducir una dependencia nueva.
`McpSessionManager` negocia `initialize` → `notifications/initialized` → `tools/list`, cachea el
catalogo, valida el major de `contract_version` contra `MCP_CONTRACT_MAJOR` y expone un
`status()` que nunca lanza, para que el resto de la aplicacion degrade con gracia. `McpAuthProvider`
es un punto de extension explicito para bearer/OAuth/mTLS futuros; hoy es un no-op porque Data
Platform MCP no tiene autenticacion. La nueva rama `app.mcp.mode=real` (independiente de
`app.integrations.mode`) permite habilitar el MCP real sin apagar el proveedor LLM `FAKE` gratuito
que Sprint 3 garantiza por defecto.

**El backend es siempre el unico orquestador MCP.** `ChatService.GenerationState` nunca delega la
conexion MCP al proveedor LLM aunque este soporte "remote MCP": ofrece el catalogo de tools
descubierto, acumula los deltas de tool-call de cada proveedor, valida cada llamada contra el
catalogo (allowlist), la ejecuta contra `McpGateway.call(...)` en un executor dedicado
(`mcpToolOrchestrationExecutor`, nunca en el hilo Reactor Netty que entrega los chunks), aplica un
timeout por-tool (`app.mcp.tool-call-timeout`) y un limite de tamano de resultado
(`app.chat.max-tool-result-bytes`), y detiene la generacion en `FAILED` con
`MCP_TOOL_ROUNDS_EXCEEDED` si se supera `app.chat.max-tool-rounds`. Un resultado con `isError:true`
o bloqueado por el allowlist nunca se reescribe como exito.

**Tool calling multi-proveedor solo para OpenAI y Anthropic este sprint**, los unicos con
`toolCalling=SUPPORTED` declarado. `ProviderStreamingSupport` gano parsing de `tool_calls`
(Responses API: `response.output_item.added`, `response.function_call_arguments.delta`) y `tool_use`
(Messages API: `content_block_start`, `input_json_delta`) solo en esos dos parsers; BytePlus,
OpenAI-compatible y Ollama conservan su comportamiento de Sprint 3 sin cambios, documentado como
fuera de alcance.

**Persistencia en una tabla hija, no un nuevo rol de mensaje.** `chat.message_tool_call`
(migracion `V5`) cuelga de `chat.message` sin tocar `message_role_metadata_check` ni el indice unico
`message_one_active_generation_uq`: todas las rondas de tool-calling de una generacion ocurren
dentro de la misma fila `ASSISTANT` ya creada por `createGeneration`, preservando intacto el
invariante de una sola generacion activa por conversacion que fijo ADR-0008.

## Consecuencias

- Mismo perimetro de dependencias que ya protege `HexagonalArchitectureTest`: ni `domain` ni
  `application` importan `org.springframework.ai..` ni `io.modelcontextprotocol..`.
- Control total de timeouts, limites de bytes y SSRF sobre el transporte MCP, al costo de mas
  codigo propio que mantener frente a un SDK.
- El chat sin tools sigue funcionando si el MCP real cae a mitad de una generacion (degradacion
  graciosa); el proveedor `FAKE` sigue disponible en `compose.dev.yaml` aunque `app.mcp.mode=real`
  este activo, porque ambos flags son independientes.
- BytePlus, OpenAI-compatible y Ollama no ofrecen tools todavia; extenderlos requiere anadir su
  propio parsing de tool-calls en `ProviderStreamingSupport` y confirmar su capacidad real antes de
  moverlos a `TOOL_CALLING_PROVIDERS`.
- Sin autenticacion MCP hoy; `McpAuthProvider` documenta la frontera de confianza (solo el backend
  alcanza `ai-platform`) y deja el punto de extension listo para cuando Data Platform MCP la
  incorpore.
