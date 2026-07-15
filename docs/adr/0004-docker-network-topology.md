# ADR-0004: Topología Docker con ingreso y redes segmentadas

- Estado: Accepted
- Fecha: 2026-07-15

## Contexto

La aplicación debe compartir red con Data Platform MCP sin exponer PostgreSQL ni conectar innecesariamente frontend y base a esa plataforma.

## Decisión

Crear `chat-ingress` para publicar únicamente Nginx, `chat-internal` como red interna de Compose y consumir `ai-platform` como red externa preexistente. Frontend conecta ingreso con la red interna; backend conecta la red interna con `ai-platform`. PostgreSQL permanece sólo en la red interna y sin puerto de host.

## Consecuencias

La segmentación reduce movimiento lateral y deja clara la responsabilidad de ingreso y egress. La red adicional es necesaria porque Docker no publica al host un contenedor conectado únicamente a una red marcada `internal`. El operador debe crear la red externa antes de iniciar Compose. El fake MCP del overlay vive sólo en `ai-platform`.
