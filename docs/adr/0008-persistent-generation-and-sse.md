# ADR-0008 — Ciclo de generación persistente y SSE

- Estado: aceptado
- Fecha: 2026-07-15

## Contexto

Sprint 3 necesita streaming multi-proveedor, cancelacion y recuperacion del contenido parcial sin
acoplar el caso de uso a Reactor, Spring MVC o los formatos de cada API. El navegador puede
desconectarse y un usuario puede cambiar la seleccion de modelo entre respuestas.

## Decision

El caso de uso crea primero un mensaje `USER/COMPLETED` y un mensaje `ASSISTANT/STREAMING` dentro de
una transaccion. `LlmChatGateway` resuelve una conexion y modelo propiedad del usuario, y cada
adaptador normaliza su protocolo a `Flow.Publisher<LlmChunk>`. `ChatService` persiste cada parcial y
es el dueño único de la transicion a `COMPLETED`, `CANCELLED` o `FAILED`.

La frontera web adapta ese publisher a SSE mediante `SseEmitter`. Cancelar la suscripcion —por
boton, timeout o desconexion— cancela también el upstream y conserva el parcial. PostgreSQL aplica
orden bajo lock e impide dos asistentes activos por conversacion mediante indice parcial. Cada
mensaje conserva el snapshot de proveedor/modelo; la conversacion conserva sólo la seleccion para
la siguiente generacion.

## Consecuencias

- El dominio y la aplicacion no dependen de los eventos concretos de OpenAI, Anthropic, BytePlus u
  Ollama.
- La cancelacion tiene el mismo resultado observable para todos los proveedores y para el fake.
- Persistir cada delta favorece recuperacion y trazabilidad pero aumenta escrituras; se debe medir y
  agrupar deltas si el volumen futuro lo exige sin perder el ultimo parcial.
- El estado activo en memoria no sobrevive a una caida abrupta. La restriccion de base evita una
  segunda generacion, por lo que hardening debe reconciliar filas `STREAMING` huerfanas al arrancar.
- SSE conserva proxy mismo-origen y CSRF; no se introducen WebSockets ni SDKs de proveedor.
