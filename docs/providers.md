# Proveedores LLM

## Estado de Sprint 3

Cada usuario administra sus propias conexiones mediante `ProviderManagementUseCase`. El dominio y
los casos de uso no dependen de SDKs externos: los clientes HTTP, JPA y el cifrado son adaptadores
intercambiables. Sprint 2 permite guardar, editar, eliminar, probar y sincronizar conexiones y
modelos. Sprint 3 añade generacion streaming mediante un gateway que primero resuelve ownership,
modelo y capacidades, y sólo después descifra la credencial en backend.

| Tipo | Base y autenticación | Prueba | Descubrimiento |
| --- | --- | --- | --- |
| `OPENAI` | `https://api.openai.com/v1`, bearer API key | `GET /models` | `GET /models` |
| `ANTHROPIC` | `https://api.anthropic.com/v1`, `x-api-key` y `anthropic-version: 2023-06-01` | `GET /models` | `GET /models?limit=1000` |
| `BYTEPLUS` | Región permitida de ModelArk y bearer `ARK_API_KEY` | `POST /chat/completions` con salida máxima de un token | No se inventa un catálogo: conserva el model ID o endpoint ID como `CONFIGURED` |
| `MINIMAX` | `https://api.minimax.io/v1`, bearer API key | `POST /chat/completions` con salida máxima de un token | No expone catálogo público: conserva el model ID como `CONFIGURED` |
| `OPENAI_COMPATIBLE` | Base URL, API key y rutas configurables | Catálogo, Responses o Chat Completions según configuración | Best-effort si existe `modelsPath`; si no, conserva el ID configurado |
| `OLLAMA` | Base URL interna, sin clave por defecto | `GET /api/tags` | `GET /api/tags` |
| `FAKE` | En proceso, sin red ni clave | Determinista | `fake-chat-v1` |

El chat usa OpenAI Responses (`response.output_text.delta`), Anthropic Messages SSE,
BytePlus/MiniMax/OpenAI-compatible Chat Completions SSE y Ollama NDJSON. OpenAI-compatible también
puede usar Responses según su ruta configurada. Todos se traducen a un stream interno con
contenido, terminacion, uso, razon y request ID opcionales. Las pruebas de contrato levantan un
servidor HTTP local y nunca invocan APIs pagadas.

Fuentes oficiales verificadas el 2026-07-15 (MiniMax verificado el 2026-07-17):

- [OpenAI Models API](https://platform.openai.com/docs/api-reference/models/list)
- [OpenAI Responses](https://platform.openai.com/docs/api-reference/responses)
- [Anthropic Models API](https://platform.claude.com/docs/en/api/models/list)
- [Anthropic Messages API](https://platform.claude.com/docs/en/api/messages/create)
- [BytePlus compatible con OpenAI](https://docs.byteplus.com/api/docs/ModelArk/1330626)
- [MiniMax Chat Completions compatible con OpenAI](https://platform.minimax.io/docs/api-reference/text-chat-openai)
- [Ollama: listar modelos](https://docs.ollama.com/api/tags)
- [OpenAI Responses streaming](https://developers.openai.com/api/reference/resources/responses/methods/create)
- [Anthropic Messages streaming](https://platform.claude.com/docs/en/build-with-claude/streaming)
- [BytePlus Chat Completions](https://docs.byteplus.com/en/docs/ModelArk/1494384)
- [Ollama Chat](https://docs.ollama.com/api/chat)

## Credenciales

La API acepta una clave sólo al crear una conexión o reemplazarla. Se cifra inmediatamente con
AES-256-GCM usando nonce aleatorio de 96 bits, tag de 128 bits, AAD versionado y
`CREDENTIAL_KEY_VERSION`. PostgreSQL almacena ciphertext, nonce, versión y una pista de cuatro
caracteres. Las respuestas sólo incluyen `credentialMasked`; nunca incluyen clave, ciphertext o
nonce.

`CREDENTIAL_MASTER_KEY` debe ser una clave base64 que decodifique exactamente a 32 bytes y se
inyecta fuera de PostgreSQL. El backend falla al arrancar si falta o no es válida. La versión se
persiste para una futura rotación; Sprint 2 sólo carga una versión activa.

```bash
openssl rand -base64 32
```

## Modelos y capacidades

Los modelos tienen origen `DISCOVERED`, `MANUAL` o `CONFIGURED`, fechas de sincronización y
validación y capacidades triestado: `SUPPORTED`, `UNSUPPORTED` o `UNKNOWN`. No se deducen
capacidades a partir del nombre. Las capacidades del adaptador expresan qué contratos sabe manejar
la integración; las capacidades por modelo permanecen `UNKNOWN` cuando el catálogo no publica
metadata fiable.

Un model ID manual se valida mediante catálogo cuando existe. Si el proveedor no ofrece catálogo,
se realiza una invocación acotada sólo cuando el usuario solicita explícitamente validarlo. BytePlus
y MiniMax usan una salida máxima de un token; esa acción puede tener coste en una cuenta real.

## API

```text
GET    /api/providers
POST   /api/providers
PUT    /api/providers/{id}
DELETE /api/providers/{id}
POST   /api/providers/{id}/test
POST   /api/providers/{id}/models/sync
GET    /api/providers/{id}/models
POST   /api/providers/{id}/models
PUT    /api/providers/{id}/models/default
```

Todas las consultas incluyen simultáneamente `id` y `owner_id`; ser `ADMIN` no concede acceso a
conexiones ajenas. Las escrituras requieren CSRF y los cambios relevantes se auditan sin claves ni
cuerpos de proveedor.

## SSRF y red

OpenAI-compatible y Ollama exigen que el host exacto esté en `PROVIDER_ALLOWED_HOSTS`. HTTP sólo se
permite si además aparece en `PROVIDER_ALLOWED_HTTP_HOSTS`; HTTPS es obligatorio para el resto. Se
rechazan userinfo, query/fragment en la base, redirects, destinos no autorizados y direcciones
link-local/metadata. El cliente limita timeout y bytes de respuesta y no registra cuerpos.

Ejemplo para un Ollama en la red Docker:

```dotenv
PROVIDER_ALLOWED_HOSTS=ollama
PROVIDER_ALLOWED_HTTP_HOSTS=ollama
```

La allowlist vacía bloquea todos los destinos configurables. El backend no debe exponerse como un
proxy genérico y la resolución DNS del host se vuelve a validar en cada operación.
