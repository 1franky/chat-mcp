# Integración MCP

## Frontera

`McpGateway` es el único contrato que application conoce. La versión estable seleccionada del protocolo es `2025-11-25`; el transporte remoto Streamable HTTP no se implementa todavía.

Sprint 0 contiene dos dobles:

1. `FakeMcpGateway` en proceso, para pruebas unitarias y el endpoint de estado.
2. WireMock en `test-support/fake-mcp`, para comprobar mensajes JSON-RPC HTTP de forma aislada.

Ambos publican únicamente las tools que la especificación confirma para Data Platform MCP:

- `health_check`
- `hello_world`

No se inventan catálogo de bases, SQL, reportes u otras capacidades futuras. `tools/call` rechaza por contrato cualquier nombre no permitido.

## Compatibilidad

El fake responde a `initialize`, notificación `notifications/initialized`, `tools/list` y `tools/call`. Declara contrato de fixture `1.0.0`; no afirma ser una implementación completa del SDK o del servidor real.

Variables reservadas:

- `MCP_BASE_URL=http://data-platform-mcp:8000`
- `MCP_ENDPOINT=/mcp`
- `MCP_PROTOCOL_VERSION=2025-11-25`
- `MCP_CONTRACT_MAJOR=1`

## Red y propiedad

Sólo `chat-backend` puede alcanzar `ai-platform`. El overlay de desarrollo coloca el fake en esa red sin publicar puerto. Este repositorio no modifica ni administra Data Platform MCP.

## Paso futuro

Un adaptador remoto deberá usar Spring AI o el SDK oficial Java detrás de `McpGateway`, negociar versión/capabilities, aplicar timeouts y límites, verificar la versión mayor del contrato y probarse contra una instancia controlada antes de habilitarse.
