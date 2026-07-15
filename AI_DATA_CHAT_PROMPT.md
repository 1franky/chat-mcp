

# Proyecto: plataforma web de chat con múltiples LLM, MCP y RAG

Actúa como arquitecto de software senior y desarrollador full stack especializado en:

- Java 21.
- Spring Boot.
- Spring Security.
- Spring Data JPA.
- PostgreSQL y pgvector.
- Angular.
- Docker y Docker Compose.
- Model Context Protocol (MCP).
- Integración con múltiples proveedores LLM.
- RAG, embeddings y procesamiento seguro de documentos.
- Seguridad de aplicaciones web y administración de secretos.
- Pruebas unitarias, integración, contratos y E2E.

Debes diseñar e implementar incrementalmente una plataforma web de chat inspirada en la experiencia de uso de ChatGPT, pero con identidad visual y código propios.

No copies marcas, logotipos, estilos propietarios ni assets de ChatGPT u Open WebUI.

La aplicación reemplazará completamente a Open WebUI y vivirá en un proyecto independiente denominado provisionalmente:

**AI Data Chat**

Antes de modificar código:

1. Inspecciona todo el repositorio.
2. Busca y respeta `AGENTS.md` o instrucciones equivalentes.
3. Determina si el repositorio está vacío o ya contiene implementación.
4. Revisa el estado de Git y conserva cambios existentes.
5. Consulta documentación oficial y actual de las tecnologías antes de fijar versiones.
6. Propón una arquitectura y un plan por sprints.
7. No implementes varios sprints simultáneamente.
8. No comiences un sprint posterior sin aprobación explícita.
9. Al terminar cada sprint, entrega pruebas y evidencia reproducible.

---

# 1. Objetivo general

Construir una aplicación web multiusuario que permita:

1. Registrarse e iniciar sesión.
2. Mantener conversaciones persistentes.
3. Conectar credenciales de diferentes proveedores LLM.
4. Consultar dinámicamente los modelos disponibles para cada conexión.
5. Elegir proveedor y modelo por conversación.
6. Cambiar de modelo dentro de una conversación conservando en cada mensaje qué proveedor y modelo lo generó.
7. Mostrar respuestas mediante streaming.
8. Usar herramientas MCP desde el chat.
9. Consultar bases de datos mediante lenguaje natural a través de Data Platform MCP.
10. Subir documentos y utilizarlos mediante RAG.
11. Mostrar citas hacia los documentos recuperados.
12. Administrar usuarios con dos roles: `ADMIN` y `USER`.
13. Ejecutarse completamente con Docker Compose.
14. Ser compatible con Linux ARM64 y un VPS Oracle Cloud Free Tier.
15. Operar sin depender de Open WebUI.

---

# 2. Sistema externo existente: Data Platform MCP

Existe otro proyecto Docker llamado **Data Platform MCP**.

No copies su código ni lo acoples como dependencia interna. Trátalo como un servicio externo con contratos MCP versionados.

Estado actual:

- Servidor: `0.5.0`.
- Contrato MCP: `1.0.0`.
- Transporte: MCP Streamable HTTP.
- URL dentro de la red Docker:

  `http://data-platform-mcp:8000/mcp`

- Health administrativo:

  `http://data-platform-mcp:8000/health`

- Red Docker externa compartida:

  `ai-platform`

Actualmente expone 15 herramientas:

1. `health_check`
2. `hello_world`
3. `list_connections`
4. `get_connection_capabilities`
5. `test_connection`
6. `refresh_schema_cache`
7. `get_schema_cache_status`
8. `search_catalog`
9. `list_schemas`
10. `list_tables`
11. `describe_table`
12. `list_relationships`
13. `validate_sql`
14. `execute_read_query`
15. `explain_query`

El MCP ya proporciona:

- Conexiones PostgreSQL readonly.
- Catálogo de schemas, tablas, columnas, PK, índices y FK.
- Caché de metadata.
- Validación SQL mediante AST.
- Ejecución exclusiva de `SELECT`.
- Límites de tiempo, filas, bytes y concurrencia.
- Parámetros nombrados.
- `EXPLAIN` sin `ANALYZE`.
- Auditoría sin almacenar SQL, parámetros ni resultados.
- Bloqueo de DML, DDL, múltiples sentencias y escrituras ocultas.
- Contratos estructurados y versionados.

El MCP no proporciona todavía:

- Generación natural desde un LLM.
- Reportes.
- Lectura de procedimientos o triggers.
- RAG.
- Autenticación MCP.
- Herramientas administrativas de usuarios.

La nueva aplicación debe encargarse de la conversación y orquestación del LLM. El MCP debe continuar independiente de cualquier proveedor LLM.

No implementes validación SQL duplicada en Spring Boot. La seguridad definitiva de la consulta pertenece al MCP.

---

# 3. Límites de responsabilidad

## Angular

Angular será responsable únicamente de:

- Interfaz.
- Navegación.
- Formularios.
- Estado visual.
- Streaming de respuestas.
- Visualización de Markdown.
- Administración visual de conversaciones, documentos, proveedores y usuarios.
- Mostrar llamadas MCP y sus estados.

Angular nunca debe:

- Recibir secretos descifrados.
- Llamar directamente a OpenAI, Anthropic, BytePlus u otros proveedores.
- Llamar directamente al MCP.
- Ejecutar SQL.
- Construir autorización sólo mediante guards del frontend.

## Spring Boot

Spring Boot será responsable de:

- Autenticación y autorización.
- Usuarios y roles.
- Sesiones.
- Persistencia de chats y mensajes.
- Cifrado de credenciales.
- Integración con proveedores LLM.
- Descubrimiento de modelos.
- Streaming.
- Orquestación del ciclo de herramientas.
- Cliente MCP.
- Subida y procesamiento de archivos.
- RAG y embeddings.
- Auditoría de seguridad.
- Aplicar ownership y aislamiento entre usuarios.

## Data Platform MCP

El MCP seguirá siendo responsable de:

- Catálogo de bases de datos.
- Metadata técnica.
- Validación SQL.
- Ejecución readonly.
- Límites de consultas.
- Auditoría técnica de consultas.
- Adaptadores de motores.
- Reportes y objetos de base de datos cuando sus siguientes sprints los incorporen.

---

# 4. Arquitectura requerida

Utiliza un monorepo con esta estructura aproximada:

```text
ai-data-chat/
├── backend/
│   ├── src/main/java/
│   ├── src/main/resources/
│   ├── src/test/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/
│   ├── src/
│   ├── angular.json
│   ├── package.json
│   └── Dockerfile
├── deployment/
│   └── nginx/
├── docs/
│   ├── architecture.md
│   ├── security.md
│   ├── providers.md
│   ├── mcp-integration.md
│   └── rag.md
├── compose.yaml
├── compose.dev.yaml
├── .env.example
├── README.md
├── TASKS.md
└── CHANGELOG.md
```

Usa arquitectura limpia o hexagonal en el backend:

```text
domain
application
ports
adapters
infrastructure
web
configuration
```

El dominio no debe depender directamente de SDKs de OpenAI, Anthropic, BytePlus, Spring AI o MCP.

Define puertos como:

```text
LlmProviderPort
ModelCatalogPort
McpGateway
EmbeddingProviderPort
DocumentStoragePort
VectorSearchPort
CredentialCipherPort
ConversationRepository
DocumentRepository
AuditRepository
```

---

# 5. Tecnologías

## Backend

- Java 21.
- Última versión estable de Spring Boot compatible con Java 21.
- Maven Wrapper.
- Spring Web MVC.
- `WebClient` para streaming y proveedores externos.
- Spring Security.
- Spring Session JDBC.
- Spring Data JPA.
- Flyway.
- Bean Validation.
- PostgreSQL.
- pgvector.
- Actuator.
- Respuestas de error Problem Details.
- Micrometer.
- Testcontainers.
- JUnit 5.
- ArchUnit.

Usa Spring AI o el SDK Java oficial de MCP para Streamable HTTP, siempre detrás de `McpGateway`.

Spring AI puede facilitar MCP y proveedores, pero el dominio no debe acoplarse a sus clases.

## Frontend

- Última versión estable de Angular.
- Componentes standalone.
- TypeScript estricto.
- Angular Signals para estado local.
- RxJS para streaming y operaciones asíncronas.
- Typed Reactive Forms.
- Angular Router.
- Angular Material/CDK.
- SCSS.
- Sanitización estricta de Markdown y HTML.
- Diseño responsive y accesible.
- Playwright para E2E.

No introduzcas NgRx inicialmente salvo que exista una necesidad demostrada.

## Persistencia

Utiliza una imagen PostgreSQL que incluya `pgvector`.

La misma instancia puede manejar:

- Usuarios.
- Sesiones.
- Proveedores.
- Modelos sincronizados.
- Conversaciones.
- Mensajes.
- Documentos.
- Chunks.
- Vectores.
- Auditoría de la aplicación.

Separa las áreas mediante tablas claramente delimitadas o schemas como:

```text
identity
chat
rag
audit
```

Los binarios de los archivos no deben almacenarse como `bytea`. Usa un volumen Docker detrás de `DocumentStoragePort`, dejando preparada una futura implementación S3/MinIO.

---

# 6. Usuarios, registro y roles

Sólo existen dos roles:

```text
ADMIN
USER
```

Ambos roles pueden:

- Crear, leer, renombrar y eliminar sus propios chats.
- Configurar sus propias credenciales de proveedores.
- Sincronizar modelos.
- Elegir modelos.
- Usar MCP.
- Subir, indexar y eliminar sus propios documentos.
- Usar RAG.
- Descargar sus propios reportes.
- Consultar su propio historial.

El administrador únicamente obtiene estas capacidades adicionales:

- Listar usuarios.
- Crear usuarios.
- Eliminar usuarios.
- Promover usuarios de `USER` a `ADMIN`.
- Si se implementa degradación, cambiar `ADMIN` a `USER` sin eliminar al último administrador.

Ser administrador no debe conceder automáticamente acceso a:

- Chats de otros usuarios.
- Documentos de otros usuarios.
- API keys de otros usuarios.
- Mensajes de otros usuarios.

## Primer registro

La primera cuenta creada en una base vacía debe convertirse en `ADMIN`.

Todos los registros posteriores deben obtener `USER`.

La asignación del primer administrador debe ser atómica y segura ante dos registros simultáneos. Usa una transacción serializable, bloqueo de base o mecanismo equivalente probado.

Nunca debe ser posible:

- Tener dos “primeros usuarios”.
- Eliminar al último administrador activo.
- Degradar al último administrador.
- Que un usuario común invoque endpoints administrativos.
- Escalar privilegios modificando el cuerpo de una petición.

Permite configurar:

```text
ALLOW_PUBLIC_REGISTRATION=true|false
```

Con registro público desactivado, sólo el administrador podrá crear usuarios después del bootstrap inicial.

---

# 7. Autenticación y sesiones

Para esta aplicación web first-party utiliza:

- Spring Security.
- Sesiones server-side persistidas con Spring Session JDBC.
- Cookie `HttpOnly`.
- `Secure` en producción.
- `SameSite`.
- Protección CSRF.
- Rotación del identificador de sesión al autenticar.
- Cierre de sesión con invalidación real.
- Contraseñas con Argon2id o un algoritmo recomendado por Spring Security.
- Rate limiting para login y registro.
- Mensajes de autenticación que no permitan enumerar usuarios.

No guardes JWT ni secretos en `localStorage`.

Endpoints mínimos:

```text
GET    /api/auth/bootstrap
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/logout
GET    /api/auth/me

GET    /api/admin/users
POST   /api/admin/users
PATCH  /api/admin/users/{id}/role
DELETE /api/admin/users/{id}
```

---

# 8. Proveedores LLM

Implementa una abstracción extensible:

```text
LlmProviderAdapter
├── OpenAiProviderAdapter
├── AnthropicProviderAdapter
├── BytePlusProviderAdapter
├── OpenAiCompatibleProviderAdapter
└── OllamaProviderAdapter
```

Cada adaptador debe declarar sus capacidades:

```text
chat
streaming
toolCalling
structuredOutput
vision
embeddings
modelDiscovery
```

Métodos conceptuales:

```text
testConnection()
listModels()
streamChat()
getCapabilities()
normalizeError()
estimateOrReadUsage()
```

No asumas que todos los proveedores ni todos los modelos soportan herramientas, visión, streaming o embeddings.

## OpenAI

- Utiliza una API key de OpenAI Platform.
- No describas una suscripción ChatGPT Plus como API key.
- Prefiere la API actual recomendada oficialmente para respuestas y herramientas.
- Consulta modelos accesibles con la API cuando sea posible.
- No hardcodees un catálogo que se vuelva obsoleto.

## Anthropic

- Utiliza una API key de Claude Console o autenticación empresarial oficialmente soportada.
- No copies ni reutilices tokens OAuth almacenados por Claude Code.
- Usa Messages API y su endpoint de modelos.
- Implementa tool use mediante el contrato nativo de Anthropic.

## BytePlus ModelArk

- Utiliza `ARK_API_KEY`.
- Permite configurar la región.
- Soporta su API `/api/v3`.
- Puede reutilizar parte del adaptador OpenAI-compatible, pero conserva una clase específica para normalizar modelos, capacidades y errores.
- Permite model ID o endpoint ID.

## Genérico OpenAI-compatible

Configuración:

```text
displayName
baseUrl
apiKey
modelsPath opcional
responsesPath opcional
chatCompletionsPath opcional
```

Por seguridad:

- HTTPS obligatorio, excepto endpoints internos explícitamente permitidos.
- Bloquea loopback, link-local, metadata cloud y redes privadas no autorizadas.
- Usa una allowlist de hosts configurada por el operador.
- No sigas redirects hacia hosts no autorizados.
- Aplica límites de tamaño, timeout y conexiones.

## Credenciales

Cada usuario administrará sus propias conexiones.

Las credenciales deben:

- Cifrarse en backend con AES-256-GCM o envelope encryption.
- Usar nonce aleatorio por registro.
- Utilizar una clave maestra fuera de PostgreSQL.
- Obtener la clave maestra mediante Docker Secret o variable protegida.
- Registrar versión de clave para futura rotación.
- No devolverse mediante API después de guardarse.
- Mostrarse únicamente en forma enmascarada.
- No aparecer en logs, excepciones, métricas o auditoría.
- Redactar `Authorization`, `x-api-key` y campos similares.

Endpoints mínimos:

```text
GET    /api/providers
POST   /api/providers
PUT    /api/providers/{id}
DELETE /api/providers/{id}
POST   /api/providers/{id}/test
POST   /api/providers/{id}/models/sync
GET    /api/providers/{id}/models
```

El descubrimiento de modelos será best-effort.

Si un proveedor no ofrece un endpoint que enumere exactamente los modelos accesibles:

- Permite ingresar manualmente el model ID.
- Valídalo con una llamada acotada.
- No inventes modelos.
- Muestra el origen: `DISCOVERED`, `MANUAL` o `CONFIGURED`.
- Guarda fecha de última sincronización.
- Muestra capacidades conocidas y desconocidas.

---

# 9. Conversaciones y streaming

Implementa:

- Crear conversación.
- Listar conversaciones propias.
- Renombrar.
- Eliminar.
- Buscar por título.
- Persistir mensajes.
- Regenerar una respuesta.
- Cancelar una generación activa.
- Streaming mediante SSE.
- Manejo de desconexión del navegador.
- Persistencia del mensaje parcial o marcación como cancelado.
- Métricas de tokens cuando el proveedor las entregue.
- Request ID del proveedor sin guardar secretos.

Cada mensaje del asistente debe guardar:

```text
providerConnectionId
providerType
modelId
createdAt
status
inputTokens opcional
outputTokens opcional
finishReason
```

Cambiar de modelo no debe reescribir mensajes anteriores.

La interfaz debe mostrar:

- Selector de proveedor.
- Selector de modelo.
- Badges de capacidades.
- Estado de conexión.
- Estado del MCP.
- Modelo utilizado en cada respuesta.
- Botón detener.
- Copiar respuesta.
- Render de Markdown, código y tablas.
- Tarjetas para tool calls.
- Citas RAG.
- Errores recuperables.

Sanitiza Markdown y no permitas HTML arbitrario, scripts, handlers ni URLs peligrosas.

---

# 10. Orquestación MCP

Spring Boot será el cliente MCP.

Usa Streamable HTTP contra:

```text
MCP_BASE_URL=http://data-platform-mcp:8000
MCP_ENDPOINT=/mcp
```

No declares `depends_on` hacia un servicio de otro proyecto Compose.

Al iniciar:

1. Intenta inicializar el cliente MCP.
2. Consulta `tools/list`.
3. Valida el major de `contract_version`.
4. Guarda una copia temporal del catálogo de tools.
5. Expone estado `UP`, `DEGRADED` o `DOWN`.
6. Permite que el chat normal continúe si MCP está caído.
7. Desactiva visualmente las funciones de datos cuando corresponda.

El backend debe ejecutar por sí mismo el ciclo de herramientas para que funcione con cualquier proveedor:

```text
usuario
→ recuperar contexto RAG
→ enviar historial + tools al modelo
→ recibir tool calls
→ validar tools permitidos
→ llamar MCP
→ devolver resultado al modelo
→ repetir de forma acotada
→ emitir respuesta final
```

No delegues la conexión MCP directamente al proveedor aunque éste soporte remote MCP. Mantener el ciclo en Spring Boot permite:

- Comportamiento uniforme.
- Aplicar autorización.
- Limitar herramientas.
- Auditar llamadas.
- Cambiar de proveedor.
- Evitar exponer el MCP a Internet.

Aplica:

- Allowlist de tools.
- Máximo configurable de rondas.
- Timeout global y por tool.
- Límite de tamaño de argumentos y resultados.
- Cancelación.
- Correlation ID.
- Redacción de resultados sensibles.
- Protección contra llamadas repetitivas.
- Detección de contratos incompatibles.

Para preguntas de base de datos, el comportamiento recomendado es:

1. Identificar conexión.
2. Consultar catálogo.
3. Pedir aclaración ante ambigüedad peligrosa.
4. Generar SQL usando metadata real.
5. Invocar `validate_sql`.
6. Para lectura permitida, invocar `execute_read_query`.
7. Para DML o DDL, mostrar únicamente el SQL bloqueado y advertir que no fue ejecutado.
8. Nunca implementar una confirmación que convierta escritura en ejecutable.
9. Mostrar truncamiento, periodo exacto, límites y warnings.

No uses endpoints administrativos de usuarios como herramientas LLM.

Registra cada tool call con:

```text
userId
conversationId
messageId
toolName
startedAt
duration
status
errorCode
argumentHash
```

No guardes API keys ni copies completas de resultados sensibles en auditoría.

---

# 11. RAG y archivos

El RAG pertenece a la nueva aplicación porque necesita ownership por usuario y relación con chats.

Utiliza PostgreSQL con pgvector y una abstracción `EmbeddingProviderPort`.

El modelo de embeddings debe configurarse independientemente del modelo de chat.

No mezcles vectores con dimensiones o modelos distintos dentro del mismo índice sin versionado.

Flujo:

```text
upload
→ validación
→ almacenamiento
→ extracción de texto
→ normalización
→ chunking
→ embeddings
→ indexación
→ estado READY
```

Estados mínimos:

```text
UPLOADED
PROCESSING
READY
FAILED
DELETING
```

Formatos iniciales:

- PDF.
- DOCX.
- TXT.
- Markdown.
- CSV.
- JSON.

Añade otros formatos sólo con pruebas y límites claros.

Protecciones:

- Tamaño máximo configurable.
- MIME real y extensión.
- Magic bytes.
- Nombre de archivo generado con UUID.
- Nombre original sólo como metadata sanitizada.
- Prevención de path traversal.
- Protección contra ZIP bombs y XML externo.
- Timeout de extracción.
- Máximo de páginas, caracteres y chunks.
- Nunca ejecutar macros ni contenido.
- Posibilidad de integrar antivirus.
- Hash del archivo para idempotencia.
- Borrado de archivo, chunks y vectores.
- Aislamiento estricto por `owner_id`.

Implementa inicialmente almacenamiento en volumen Docker. No acoples el dominio al filesystem.

El retrieval debe:

- Filtrar siempre por usuario.
- Permitir limitarse a documentos seleccionados en el chat.
- Combinar búsqueda vectorial y, cuando aporte valor, full-text search.
- Aplicar top-k y umbral configurables.
- Devolver citas con archivo, página o sección y chunk.
- Tratar el contenido recuperado como datos no confiables.
- Ignorar instrucciones encontradas dentro de documentos que intenten cambiar el system prompt, revelar secretos o forzar tools.

Endpoints:

```text
GET    /api/documents
POST   /api/documents
GET    /api/documents/{id}
POST   /api/documents/{id}/reindex
DELETE /api/documents/{id}
```

---

# 12. Modelo de datos mínimo

Crea migraciones Flyway para entidades equivalentes a:

```text
app_user
provider_connection
provider_model
conversation
message
tool_call
document
document_chunk
message_document
security_audit_event
```

Incluye:

- UUID.
- Timestamps en UTC.
- Optimistic locking donde sea útil.
- Foreign keys.
- Índices por ownership y fechas.
- Borrado consistente.
- Constraints para roles y estados.
- Índices vectoriales apropiados.
- Paginación.

Toda consulta de recursos de usuario debe incluir el propietario. No confíes sólo en verificar ownership después de leer el registro.

---

# 13. Interfaz Angular

Rutas mínimas:

```text
/login
/register
/chat
/chat/:conversationId
/settings/providers
/knowledge
/admin/users
```

La aplicación tendrá:

- Sidebar colapsable con conversaciones.
- Botón nuevo chat.
- Buscador.
- Área central de mensajes.
- Composer multilínea.
- Selector proveedor/modelo.
- Selector de documentos.
- Indicador MCP.
- Panel de configuración.
- Panel RAG.
- Panel de usuarios visible sólo para administradores.
- Tema claro/oscuro.
- Diseño móvil.
- Accesibilidad por teclado.
- Estados loading, empty, error y retry.

Usa guards para UX, pero toda autorización real debe permanecer en Spring Security.

Idioma inicial: español. Deja preparada internacionalización futura.

---

# 14. Docker y redes

El proyecto tendrá al menos:

```text
chat-frontend
chat-backend
chat-postgres
```

Redes:

```text
chat-internal
ai-platform (external)
```

Conectividad:

```text
Internet/Proxy
      ↓
chat-frontend / Nginx
      ↓
chat-backend
      ├── chat-postgres por chat-internal
      ├── proveedores LLM por HTTPS
      └── data-platform-mcp por ai-platform
```

Sólo `chat-backend` debe conectarse a `ai-platform`.

PostgreSQL debe permanecer en `chat-internal` y no publicar puerto al host por defecto.

Nginx debe:

- Servir Angular.
- Hacer proxy de `/api`.
- Mantener SSE sin buffering.
- Aplicar headers de seguridad.
- Permitir límites de upload configurados.
- Servir todo bajo mismo origen para simplificar cookies y CSRF.

Usa:

- Builds multi-stage.
- Usuarios no-root.
- Imágenes ARM64.
- Versiones fijadas, no `latest`.
- Healthchecks.
- Volúmenes nombrados.
- `read_only` donde sea viable.
- `cap_drop: ALL`.
- `no-new-privileges`.
- `tmpfs` para temporales.
- `.env.example` sin secretos reales.

La red externa se prepara con:

```bash
docker network inspect ai-platform >/dev/null 2>&1 ||
docker network create ai-platform
```

Variables esperadas:

```text
AI_PLATFORM_NETWORK=ai-platform
MCP_BASE_URL=http://data-platform-mcp:8000
MCP_ENDPOINT=/mcp

POSTGRES_DB=ai_data_chat
POSTGRES_USER=ai_data_chat
POSTGRES_PASSWORD=...

CREDENTIAL_MASTER_KEY=...
ALLOW_PUBLIC_REGISTRATION=true

MAX_UPLOAD_BYTES=...
MAX_TOOL_ROUNDS=...
MAX_TOOL_RESULT_BYTES=...
```

---

# 15. Seguridad

Implementa defensa en profundidad:

- OWASP ASVS como referencia.
- CSRF.
- Cookies seguras.
- CSP.
- HSTS en producción.
- `X-Content-Type-Options`.
- Política de `frame-ancestors`.
- Validación server-side.
- Límites de request.
- Rate limiting.
- Protección SSRF.
- Redacción de secretos.
- Cifrado de credenciales.
- No registrar cuerpos completos de proveedores.
- No registrar prompts o documentos en logs operativos.
- No retornar stack traces al cliente.
- Timeouts y circuit breakers.
- Backoff respetando `Retry-After`.
- Auditoría de login, logout, cambios de rol, altas y bajas.
- Protección contra prompt injection procedente de RAG o MCP.
- Ownership probado en repositorios y controllers.
- No confiar en nombres de modelos como capacidades.
- No enviar documentos completos al LLM si sólo se necesitan chunks.
- Borrado de datos del usuario claramente definido.

El MCP actual no tiene autenticación. Hasta que se implemente:

- No publicarlo en Internet.
- Mantenerlo sólo en la red Docker.
- Hacer que únicamente el backend actúe como cliente.
- Documentar esta frontera de confianza.
- Preparar soporte futuro para bearer token, OAuth de servicio o mTLS.

---

# 16. Pruebas obligatorias

## Backend

- Primer usuario se convierte en administrador.
- Dos registros simultáneos producen un solo primer administrador.
- Usuarios posteriores son `USER`.
- `USER` recibe 403 en endpoints administrativos.
- No se puede eliminar o degradar al último administrador.
- Ownership de chats, documentos y providers.
- Cifrado y descifrado de credenciales.
- Secrets ausentes en JSON y logs.
- Test de proveedor exitoso y fallido.
- Sincronización de modelos.
- Streaming y cancelación.
- Normalización de errores.
- MCP discovery.
- MCP caído.
- Contrato MCP major incompatible.
- Tool no permitida.
- Máximo de rondas.
- Resultado demasiado grande.
- SQL bloqueado nunca se presenta como ejecutado.
- RAG aislado por usuario.
- Borrado de chunks.
- Upload malicioso.
- SSRF.
- Migraciones con PostgreSQL/pgvector real usando Testcontainers.

## Frontend

- Login y registro.
- Primer administrador.
- Guards.
- Selector de proveedor/modelo.
- Streaming.
- Cancelación.
- Creación y eliminación de chat.
- Upload y estado de indexación.
- Citas.
- Panel admin.
- Manejo de 401, 403, 429 y errores de proveedor.
- Sanitización de Markdown.

## E2E

Como mínimo:

1. Registrar primer usuario.
2. Verificar que es admin.
3. Crear segundo usuario.
4. Iniciar sesión como usuario común.
5. Configurar un proveedor falso.
6. Crear un chat y recibir streaming.
7. Intentar acceder a administración y recibir rechazo.
8. Subir documento.
9. Hacer una pregunta RAG y obtener cita.
10. Invocar un MCP falso compatible.
11. Probar un `SELECT`.
12. Probar un `DELETE` y demostrar que nunca se ejecuta.

Usa fakes de proveedores y MCP. Los tests normales no deben consumir APIs pagadas.

---

# 17. Roadmap por sprints

## Sprint 0 — Descubrimiento y bootstrap

- ADRs.
- Arquitectura.
- TASKS.
- Backend mínimo.
- Frontend mínimo.
- PostgreSQL/pgvector.
- Docker Compose.
- Healthchecks.
- CI y calidad.
- Fake MCP y fake provider.

## Sprint 1 — Identidad y usuarios

- Registro.
- Primer admin.
- Login/logout.
- Sesiones.
- Roles.
- Administración de usuarios.
- Seguridad y pruebas de concurrencia.

## Sprint 2 — Proveedores y modelos

- Cifrado.
- OpenAI.
- Anthropic.
- BytePlus.
- OpenAI-compatible.
- Ollama.
- Test de conexión.
- Descubrimiento y selección de modelos.
- Capabilities.

## Sprint 3 — Chat

- Conversaciones.
- Mensajes.
- Streaming.
- Cancelación.
- Selector de modelo.
- Markdown.
- Uso y errores.
- UI estilo chat completa.

## Sprint 4 — MCP

- Cliente Streamable HTTP.
- Discovery de tools.
- Tool calling multi-proveedor.
- Integración con el MCP real.
- Tarjetas de tools.
- Flujo natural→metadata→SQL→validate→execute.
- Pruebas de contrato y seguridad.

## Sprint 5 — RAG

- Upload.
- Extracción.
- Chunking.
- Embeddings.
- pgvector.
- Retrieval.
- Citas.
- Selección de documentos.
- Seguridad de documentos.

## Sprint 6 — Capacidades futuras del MCP

- Reportes XLSX, PDF, CSV y JSON.
- Descarga autenticada.
- Procedimientos, funciones, vistas y triggers.
- Explicación con el LLM.
- Adaptación automática a tools nuevas compatibles.
- Soporte transparente para motores adicionales.

No inventes tools que todavía no existan. Implementa feature flags o fakes contractuales hasta que el MCP las publique.

## Sprint 7 — Hardening y operación

- Métricas.
- Readiness.
- Backups.
- Retención.
- Rotación de claves.
- Límites distribuidos.
- Pruebas de carga.
- TLS.
- Observabilidad.
- Guía de despliegue y rollback.

---

# 18. Relación con el roadmap del MCP

La nueva arquitectura cambia el reparto anterior:

- La generación natural y el ciclo del LLM pertenecen a AI Data Chat.
- El MCP no debe incorporar API keys de proveedores.
- La generación de reportes y seguridad SQL permanecen en el MCP.
- La explicación de objetos la hace el LLM usando definiciones obtenidas del MCP.
- El RAG de archivos subidos por usuarios pertenece inicialmente a AI Data Chat.
- El sprint antiguo de integración Open WebUI debe considerarse reemplazado por la integración con esta aplicación.
- Los nuevos adaptadores de base de datos permanecerán transparentes para la web mediante los contratos MCP.

No modifiques el proyecto Data Platform MCP desde este repositorio. Si detectas cambios necesarios en sus contratos, documéntalos como una propuesta separada.

---

# 19. Definición de terminado

Una historia no está terminada hasta que:

- Compila.
- Tiene pruebas.
- Pasa lint y formato.
- Tiene migraciones reproducibles.
- Funciona en ARM64.
- No expone secretos.
- Tiene documentación.
- Incluye criterios de aceptación verificables.
- Actualiza `TASKS.md`.
- Actualiza `README.md`.
- Actualiza `CHANGELOG.md`.
- Incluye comandos exactos de validación.
- No deja funcionalidad simulada presentada como real.

Al cerrar cada sprint entrega:

1. Resumen.
2. Archivos modificados.
3. Decisiones.
4. Comandos ejecutados.
5. Resultados de pruebas.
6. Riesgos.
7. Pendientes.
8. Solicitud de aprobación antes del siguiente sprint.

---

# 20. Primera tarea

Comienza únicamente con Sprint 0.

Primero inspecciona el repositorio y responde con:

1. Estado actual.
2. Supuestos.
3. Arquitectura propuesta.
4. Diagrama de contenedores.
5. Estructura de módulos.
6. Decisiones ADR iniciales.
7. Plan detallado de Sprint 0.
8. Riesgos y dudas realmente bloqueantes.

Después implementa Sprint 0 si el repositorio y las instrucciones permiten continuar.

No comiences Sprint 1 sin aprobación explícita.

