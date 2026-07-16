# Chat y streaming

## Alcance

El chat implementa conversaciones propias, mensajes persistentes, busqueda por titulo,
renombrado, borrado, cambio de proveedor/modelo para respuestas futuras, regeneracion, streaming y
cancelacion (Sprint 3). Sprint 4 anade tool calling multi-proveedor (solo OpenAI y Anthropic) contra
un cliente MCP Streamable HTTP real, con orquestacion de rondas en el backend — ver
`docs/mcp-integration.md` y ADR-0009. No implementa documentos, RAG ni citas.

La UI muestra el modo activo (`FAKE`/`real`) y el estado del MCP (`UP`/`DEGRADED`/`DOWN`) enlazando
al panel de solo lectura en `/settings/mcp`.

## API

```text
GET    /api/conversations?query=&page=0&size=30
POST   /api/conversations
GET    /api/conversations/{conversationId}
PUT    /api/conversations/{conversationId}/title
PUT    /api/conversations/{conversationId}/selection
DELETE /api/conversations/{conversationId}
GET    /api/conversations/{conversationId}/messages
POST   /api/conversations/{conversationId}/messages/stream
POST   /api/conversations/{conversationId}/messages/{messageId}/regenerate/stream
DELETE /api/conversations/{conversationId}/generations/{generationId}
```

Todos requieren sesion; las escrituras requieren CSRF. Un recurso ajeno se responde como no
encontrado. El stream usa eventos `generation`, `delta`, `tool_call`, `tool_result`, `complete`,
`cancelled` y `error`. El primer evento entrega IDs y mensajes persistidos; los terminales entregan
el snapshot final y un codigo normalizado sin cuerpo externo. `tool_call`/`tool_result` entregan el
snapshot del mensaje asistente (sin el arreglo de tool calls, que el frontend acumula localmente por
`generationRound`/`sequence`) y un `toolCall` con el estado individual.

## Persistencia y concurrencia

`V4__conversations_and_messages.sql` crea las dos tablas base. Las posiciones se asignan bajo lock
de la conversacion y son únicas. Un indice parcial garantiza un solo asistente `STREAMING`. El
mensaje del asistente captura `providerConnectionId`, tipo, model ID, uso opcional, `finishReason` y
request ID; cambiar la seleccion de la conversacion no reescribe mensajes anteriores.
`V5__mcp_tool_calls.sql` anade `chat.message_tool_call`, colgando de la misma fila `ASSISTANT` de la
generacion — no crea un nuevo rol de mensaje ni toca el indice de generacion unica (ver ADR-0009).

Cada delta actualiza el contenido parcial antes de emitirse al navegador. Al completar cambia a
`COMPLETED`; detener o perder el cliente cambia a `CANCELLED`; un error normalizado cambia a
`FAILED`. Alcanzar `MAX_TOOL_ROUNDS` termina la generacion en `FAILED` con
`MCP_TOOL_ROUNDS_EXCEEDED`. Un cierre abrupto de todo el proceso no ejecuta callbacks y puede dejar
`STREAMING`; una reconciliacion de arranque queda como pendiente operativo.

## Proveedores

- OpenAI: Responses API SSE, con tool calling (`function_call`).
- Anthropic: Messages API SSE, con tool calling (`tool_use`).
- BytePlus: Chat Completions compatible SSE. Sin tool calling.
- OpenAI-compatible: Responses o Chat Completions según rutas configuradas. Sin tool calling.
- Ollama: `/api/chat` NDJSON. Sin tool calling.
- Fake: publisher determinista en proceso, sin red ni coste. Sin tool calling.

Los parsers sólo emiten el texto, los deltas de tool-call y metadata contractual. No se guardan
cuerpos completos. El historial excluye respuestas fallidas/canceladas, se recorta desde los
mensajes más recientes y se limita con `CHAT_MAX_HISTORY_MESSAGES` y `CHAT_MAX_HISTORY_CHARACTERS`.

## UI

La ruta `/chat` ofrece historial, busqueda, selectores, capacidades, estado del proveedor, snapshot
por respuesta, uso, Markdown seguro, copiar, regenerar, detener, errores recuperables y tarjetas de
tool call con su estado (pendiente/ejecutando/completado/error/bloqueado/tiempo agotado). Enter
envia y Shift+Enter crea una linea. El tema oscuro se alterna desde la barra superior; sólo la
preferencia `light`/`dark` se guarda en `localStorage`. El badge de MCP enlaza al panel de solo
lectura `/settings/mcp`.
