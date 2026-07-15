# Chat y streaming

## Alcance de Sprint 3

El chat implementa conversaciones propias, mensajes persistentes, busqueda por titulo,
renombrado, borrado, cambio de proveedor/modelo para respuestas futuras, regeneracion, streaming y
cancelacion. No implementa tool calling, cliente MCP remoto, documentos, RAG ni citas.

La UI etiqueta el MCP como `FAKE`; el estado mostrado en este sprint corresponde al adaptador de
pruebas en proceso, no a Data Platform MCP.

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
encontrado. El stream usa eventos `generation`, `delta`, `complete`, `cancelled` y `error`. El
primer evento entrega IDs y mensajes persistidos; los terminales entregan el snapshot final y un
codigo normalizado sin cuerpo externo.

## Persistencia y concurrencia

`V4__conversations_and_messages.sql` crea las dos tablas. Las posiciones se asignan bajo lock de la
conversacion y son únicas. Un indice parcial garantiza un solo asistente `STREAMING`. El mensaje del
asistente captura `providerConnectionId`, tipo, model ID, uso opcional, `finishReason` y request ID;
cambiar la seleccion de la conversacion no reescribe mensajes anteriores.

Cada delta actualiza el contenido parcial antes de emitirse al navegador. Al completar cambia a
`COMPLETED`; detener o perder el cliente cambia a `CANCELLED`; un error normalizado cambia a
`FAILED`. Un cierre abrupto de todo el proceso no ejecuta callbacks y puede dejar `STREAMING`; una
reconciliacion de arranque queda como pendiente operativo.

## Proveedores

- OpenAI: Responses API SSE.
- Anthropic: Messages API SSE.
- BytePlus: Chat Completions compatible SSE.
- OpenAI-compatible: Responses o Chat Completions según rutas configuradas.
- Ollama: `/api/chat` NDJSON.
- Fake: publisher determinista en proceso, sin red ni coste.

Los parsers sólo emiten el texto y metadata contractual. No se guardan cuerpos completos. El
historial excluye respuestas fallidas/canceladas, se recorta desde los mensajes más recientes y se
limita con `CHAT_MAX_HISTORY_MESSAGES` y `CHAT_MAX_HISTORY_CHARACTERS`.

## UI

La ruta `/chat` ofrece historial, busqueda, selectores, capacidades, estado del proveedor, snapshot
por respuesta, uso, Markdown seguro, copiar, regenerar, detener y errores recuperables. Enter envia
y Shift+Enter crea una linea. El tema oscuro se alterna desde la barra superior; sólo la preferencia
`light`/`dark` se guarda en `localStorage`.
