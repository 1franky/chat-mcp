# RAG

## Estado (Sprint 5, iniciado 2026-07-17)

El propietario del proyecto aprobó arrancar el Sprint 5 explícitamente el 2026-07-17, empezando por
el esquema de base de datos y `EmbeddingProviderPort`; el mismo día aprobó los adaptadores reales y
fake para `DocumentRepository`, `DocumentStoragePort` y `VectorSearchPort`; después el endpoint de
subida `POST /api/documents` con sus protecciones; el mismo 2026-07-18 aprobó primero extracción,
normalización y chunking (documento `UPLOADED` → `READY` con chunks y embeddings reales sobre el
fake indexados), y después, con una segunda luz verde el mismo día, retrieval y citas (backend). El
2026-07-19, tras una nueva luz verde, se cerró el sprint con la UI: panel `/settings/documents`
(subir/listar con polling de estado/eliminar) y, dentro del chat, un selector de documentos por
conversación en el composer y tarjetas de cita bajo las respuestas fundamentadas. Sprint 5 queda
completo end-to-end. Ver "Bug corregido: documentos siempre reales" más abajo para un defecto de
backend descubierto y arreglado durante la verificación manual de esta UI.

### Esquema `rag` (migraciones `V6`/`V7`)

- `V6__add_minimax_provider_type.sql`: corrige las restricciones `CHECK` de
  `chat.provider_connection`/`chat.message` que se quedaron desactualizadas al añadir el proveedor
  MiniMax (no relacionado con RAG, pero necesario antes de seguir sumando migraciones).
- `V7__rag_documents_and_chunks.sql` crea:
  - `rag.document`: ownership por `owner_id`, `content_hash` único por propietario (idempotencia de
    upload), estado (`UPLOADED`/`PROCESSING`/`READY`/`FAILED`/`DELETING`), `embedding_model_id` y
    `embedding_dimension` obligatorios sólo en `READY`.
  - `rag.document_chunk`: `owner_id` denormalizado (permite filtrar por propietario directamente en
    la búsqueda vectorial, sin depender de un `JOIN`), `embedding vector(1536)` con índice HNSW
    (`vector_cosine_ops`), texto acotado a 8000 caracteres, `page_number`/`section_label` para
    citas.
  - `rag.message_document`: relación `SELECTED` (documento elegido como contexto de una pregunta) o
    `CITED` (chunk efectivamente citado en una respuesta), con índices únicos parciales que evitan
    duplicados por tipo de relación.

La dimensión del vector queda fija en `1536` (compatible con modelos de embeddings ampliamente
usados, p. ej. `text-embedding-3-small`). Cambiar de modelo de embeddings con otra dimensión
requiere una migración nueva que versione la columna o la tabla; Postgres ya rechaza a nivel de
tipo cualquier inserción con una dimensión distinta, que es la protección mínima contra mezclar
vectores de modelos distintos que exige la especificación.

Verificado el 2026-07-17 contra Postgres real (pgvector) vía Testcontainers en
`PostgresMigrationTest`: V6/V7 aplican limpio, la columna `embedding` queda en dimensión 1536, el
índice HNSW existe y `owner_id` es obligatorio en `document`/`document_chunk`.

### `EmbeddingProviderPort`

```java
public interface EmbeddingProviderPort {
    EmbeddingBatch embed(String modelId, List<String> inputs);
    record EmbeddingBatch(int dimension, List<float[]> vectors) { /* valida longitud por vector */ }
}
```

Implementación disponible: `FakeEmbeddingProviderAdapter` (modelo `fake-embedding-v1`, dimensión
1536), determinista y sin red — genera un vector unitario por hash SHA-256 del texto de entrada, de
forma que el mismo texto siempre produce el mismo vector y textos distintos producen vectores
distintos. **Desde que `DocumentProcessingService` lo consume (2026-07-18), el bean pasó a estar
siempre activo, sin `@ConditionalOnProperty` sobre `app.integrations.mode`** — no hay ningún
adaptador real todavía (no aprobado), así que bajo `mode=real` no había nada a lo que alternar y el
contexto de Spring no arrancaría sin este cambio. Es un estado deliberadamente temporal: en cuanto
se apruebe y añada un adaptador real de embeddings, este bean debe volver a condicionarse igual que
el resto de fakes.

### `DocumentRepository`, `DocumentStoragePort`, `VectorSearchPort`

```java
public interface DocumentRepository {
    boolean existsByIdAndOwnerId(UUID documentId, UUID ownerId);
    Optional<Document> findByIdAndOwnerId(UUID documentId, UUID ownerId);
    DocumentPage findAllByOwnerId(UUID ownerId, DocumentStatus statusFilter, int page, int size);
    Document save(Document document);
    void deleteByIdAndOwnerId(UUID documentId, UUID ownerId);
}
```

Implementación real `DocumentJpaAdapter` (`adapters/out/persistence/rag/`) sobre `rag.document`,
mismo patrón JPA que `ConversationJpaAdapter`: filtra siempre por `ownerId` explícito en cada query
(incluso cuando el caller ya validó ownership), optimistic locking vía `@Version`. Fake en memoria
`FakeDocumentRepository`.

`DocumentStoragePort` (`store`/`open`/`delete`, ya definido en el sprint anterior) tiene ahora
`FilesystemDocumentStorageAdapter` (`adapters/out/storage/`): escribe bajo el volumen Docker
`chat-documents` (ruta configurable con `RAG_STORAGE_PATH`, default
`/var/lib/ai-data-chat/documents`), y sanea cada `storageKey`/`generatedName` a solo su componente
de nombre de archivo antes de resolver la ruta bajo `basePath/ownerId`, para que ningún segmento
`..` pueda escapar del directorio del propietario. Fake en memoria `FakeDocumentStorageAdapter`.

`VectorSearchPort` (`deleteByDocument`, ya definido en el sprint anterior, más `replaceChunks` desde
la tarea de chunking) tiene ahora `PgVectorSearchAdapter` (`adapters/out/persistence/rag/`): **JDBC
nativo vía `JdbcTemplate` + la librería `com.pgvector:pgvector`**, no JPA — Hibernate no tiene
soporte nativo para el tipo `vector` en este stack, así que este adaptador es el único punto del
backend que usa SQL directo en lugar de un repositorio Spring Data. Decisiones de diseño:
- `index()` hace `UPDATE` de la columna `embedding` sobre chunks que **ya existen** en
  `rag.document_chunk`, nunca `INSERT`. Ningún caso de uso lo invoca todavía — queda reservado para
  un futuro reindex que solo recalcule embeddings sobre contenido ya existente (p. ej. al cambiar de
  modelo de embeddings), a diferencia de `replaceChunks`, que sí es la escritura inicial real usada
  por el chunking. Si un `chunkId` no existe, lanza `IllegalStateException`.
- `search(ownerId, documentIds, query, topK)` (firma ampliada 2026-07-18: antes no tenía
  `documentIds` — buscaba sobre *todos* los chunks del owner. Se corrigió antes de que el retrieval
  la usara, porque sin el filtro el "opt-in por documentos seleccionados" quedaría roto: chunks de
  documentos no elegidos para esa conversación podrían colar contenido no autorizado) ordena por
  distancia coseno (`<=>`) usando el índice HNSW ya creado en `V7`, acotado con `AND document_id =
  ANY(?)`; `VectorMatch.score()` es `1 - distancia` (similitud coseno). El fake usa el mismo
  criterio para ser intercambiable. Nota de rendimiento conocida: pgvector+HNSW no siempre empuja
  ese filtro dentro del índice ANN — aceptable para el volumen esperado (documentos de un usuario,
  no un corpus masivo).
- `findByIds(ownerId, chunkIds)` (nuevo 2026-07-18): resuelve contenido/página/sección completos por
  id, scoped al owner — usado tanto por el retrieval en caliente como para hidratar citas al releer
  el historial de un chat.
- Nuevo dominio `DocumentChunk` (2026-07-18): mapea 1:1 el CHECK de `rag.document_chunk`.

`DocumentJpaAdapter`, `FilesystemDocumentStorageAdapter` y `PgVectorSearchAdapter` **están siempre
activos, sin `@ConditionalOnProperty` sobre `app.integrations.mode`** desde el 2026-07-19 (ver
"Bug corregido: documentos siempre reales" más abajo) — persistir un documento o un vector es local y
gratis, igual que `EmbeddingProviderPort`. Los tres fakes en memoria (`FakeDocumentRepository`,
`FakeDocumentStorageAdapter`, `FakeVectorSearchAdapter`) siguen existiendo como implementaciones
usadas directamente por tests unitarios, pero ya no se registran como beans de Spring.

Verificado el 2026-07-17: 17 tests unitarios (fakes + `FilesystemDocumentStorageAdapterTest` con
`@TempDir`, incluye aislamiento por owner y neutralización de segmentos `..`) y 9 tests de
integración contra Postgres real vía Testcontainers (`DocumentJpaAdapterIntegrationTest`:
aislamiento por owner, constraint único `owner_id+content_hash`, optimistic locking, borrado en
cascada al eliminar el usuario; `PgVectorSearchAdapterIntegrationTest`: `index`/`search`/
`deleteByDocument` contra chunks reales, orden por similitud, aislamiento por owner;
`PostgresMigrationTest`). `./mvnw verify` completo del backend en verde (89/89).

### Upload (`POST /api/documents`)

`DocumentManagementUseCase`/`DocumentManagementService` (sin anotaciones Spring, cableado a mano)
implementa el flujo `upload → validación → almacenamiento` con las protecciones exigidas por
`AI_DATA_CHAT_PROMPT.md` para esta etapa:

- **Tamaño máximo configurable**: `spring.servlet.multipart.max-file-size`/`max-request-size` (HTTP)
  y `app.rag.upload.max-bytes` (defensa en profundidad en el servicio, lectura acotada con
  `InputStream.readNBytes(limit + 1)` — nunca vuelca un stream sin límite a memoria). Ambos
  cableados a `MAX_UPLOAD_BYTES` (25 MiB por defecto), que ya existía en `compose.yaml`/
  `.env.example` en línea con `NGINX_MAX_UPLOAD_SIZE` pero **no lo leía nada del backend** — ahora sí.
- **MIME real y extensión**: nuevo port `DocumentMimeDetectionPort` con adaptador
  `TikaDocumentMimeDetectionAdapter` (`org.apache.tika:tika-core`, primera dependencia externa nueva
  del proyecto para esto, confinada al adaptador — nunca importada desde `application`/`domain`,
  sin fake porque es determinista/local, no hay nada que evitar). Un allowlist
  `extensión → {MIME permitidos}` cruza el MIME detectado contra la extensión declarada; `pdf`/`docx`
  son estrictos (magic bytes fuertes), `txt`/`md`/`csv`/`json` toleran que Tika resuelva
  `text/plain` (no tienen magic bytes fiables) sin dejar de rechazar binarios reales disfrazados
  (un ejecutable nunca se detecta como `text/plain` ni `application/pdf`).
- **Nombre de archivo generado por UUID**: `DocumentStoragePort.store` recibe un `UUID.randomUUID()`
  puro como nombre de storage; el nombre original solo se sanea como metadata (`[A-Za-z0-9 ._-]`,
  256 chars), nunca se usa para resolver rutas.
- **Protección ZIP-bomb**: solo para `.docx` (único formato basado en ZIP de los 6 soportados) —
  `java.util.zip.ZipInputStream` descomprime con un contador acumulado (nunca confía en el tamaño
  declarado por la entrada) y aborta apenas se superan 100 MB inflados o 2000 entradas, sin
  necesitar terminar de inflar el resto.
- **Hash para idempotencia**: SHA-256 de los bytes completos; `DocumentRepository.
  findByOwnerIdAndContentHash` (nuevo método del port) hace que volver a subir el mismo contenido
  para el mismo propietario devuelva el documento ya existente (`created=false`) en vez de
  duplicarlo. El constraint único `owner_id+content_hash` de `V7` cubre la carrera concurrente rara.
- **Aislamiento estricto por `owner_id`**: `GET /api/documents`, `GET /api/documents/{id}`,
  `DELETE /api/documents/{id}` filtran siempre por propietario; otro owner recibe `404` (nunca
  `403`, para no filtrar existencia).

Endpoints implementados: `POST /api/documents`, `GET /api/documents`, `GET /api/documents/{id}`,
`DELETE /api/documents/{id}`. `POST /api/documents/{id}/reindex` queda fuera (depende de chunking/
embeddings reales, sin aprobar). El documento queda en estado `UPLOADED`; borrar limpia el archivo
del storage y la fila (el `ON DELETE CASCADE` de `V7` ya cubre `document_chunk`/`message_document`
cuando existan, así que `deleteDocument` no necesita invocar `VectorSearchPort`).

**Explícitamente diferido, documentado pero no implementado aquí** (no son responsabilidad de la
etapa de upload, sino de la futura extracción, que es la que realmente parsea/abre contenido):
protección XXE (se aplicaría al parsear XML/OOXML durante la extracción), "nunca ejecutar macros ni
contenido embebido" (idem), timeout de extracción, límites de páginas/caracteres/chunks. Antivirus
sigue siendo opcional según la especificación. Tampoco existe un endpoint de descarga/apertura del
archivo (no hay caso de uso que lo necesite todavía).

Verificado el 2026-07-17: 13 tests unitarios (`DocumentManagementServiceTest`, con fixtures reales
de PDF/DOCX — un OOXML mínimo válido construido con `ZipOutputStream` — y un docx-bomba que infla a
101 MB desde bytes muy comprimibles), 5 del adaptador Tika, 4 de integración HTTP end-to-end
(`DocumentControllerIntegrationTest`: multipart + CSRF contra Postgres/filesystem reales, 401 sin
sesión, 403 sin CSRF, aislamiento por owner vía HTTP), más 2 nuevos para
`findByOwnerIdAndContentHash` (`FakeDocumentRepositoryTest`, `DocumentJpaAdapterIntegrationTest`).
`./mvnw verify` completo del backend en verde (113/113).

### Extracción, normalización y chunking

Aprobado el 2026-07-18. `DocumentManagementService.uploadDocument` dispara, tras guardar un
documento **nuevo** (`created=true`) y su auditoría, `documentProcessingExecutor.execute(() ->
documentProcessing.processDocument(ownerId, documentId))` — fire-and-forget sobre un
`ExecutorService` propio (`documentProcessingExecutor`, cached pool, **separado** de
`mcpToolOrchestrationExecutor` para no competir por hilos con el tool-calling del chat). La
respuesta HTTP del upload sigue siendo síncrona e inmediata; el documento queda en `UPLOADED` y el
pipeline corre después.

`DocumentProcessingUseCase`/`DocumentProcessingService` (nuevo, sin anotaciones Spring) orquesta:

```text
PROCESSING → extracción (con timeout) → normalización + chunking → embeddings por lotes
           → replaceChunks (indexación) → READY
```

- **Extracción**: nuevo puerto `DocumentTextExtractionPort` (`extract(extension, mimeType,
  content)` → lista de `ExtractedPage(pageNumber, sectionLabel, text)`) con un único adaptador
  `DocumentTextExtractorAdapter` (`adapters/out/extraction/`), sin fake — determinista y 100% local,
  mismo precedente que la detección MIME con Tika. El dispatch es por **extensión**, no por MIME
  detectado: `text/plain` es ambiguo entre txt/md/csv/json (varios de esos formatos no tienen magic
  bytes fiables y el upload ya los deja caer a `text/plain`), así que el MIME solo sirve como dato
  informativo aquí.
  - PDF: `org.apache.pdfbox:pdfbox` — una `ExtractedPage` por página real, 1-indexada (coincide con
    el CHECK `page_number > 0`). `max-pages`/`max-characters` (`app.rag.extraction.*`) se hacen
    cumplir **dentro del propio adaptador**, lo antes posible (número de páginas comprobado antes de
    extraer ningún texto; total de caracteres comprobado incrementalmente página a página, abortando
    en cuanto se supera) — protección real contra agotamiento de recursos, no solo contabilidad
    posterior.
  - DOCX: `org.apache.poi:poi-ooxml` — texto completo como una única página (`pageNumber`/
    `sectionLabel` ambos `null`). Deliberadamente **no** se trocea por estilos de heading (`Heading1`,
    etc.): esos estilos varían entre versiones/locales de Word y depender de ellos habría sido frágil
    para un v1; POI ya deshabilita XXE por defecto desde la rama 4.x (`XMLHelper`) y trae su propio
    guardián anti zip-bomb (`ZipSecureFile`, defensa en profundidad sobre la ya existente en el
    upload) — verificado con un test que craftea un `.docx` con una entidad XML externa apuntando a
    un archivo local y confirma que nunca se resuelve en el texto extraído.
  - Markdown: troceado en secciones por encabezado (`^#{1,6}\s+.+$`), `sectionLabel` = texto del
    heading (recortado a 255 caracteres, el límite del CHECK), `pageNumber` siempre `null`.
  - TXT/CSV/JSON: decodificados como UTF-8 **estricto** (`CodingErrorAction.REPORT`) — contenido que
    no es UTF-8 válido aborta con `unsupported_text_encoding` en vez de decodificar con reemplazo
    silencioso, que corrompería lo que luego se indexa y cita.
- **Normalización + chunking**: `TextChunker` (`application/service/`, clase pura sin Spring).
  Normaliza preservando la distinción entre salto de línea simple y doble (un salto doble marca un
  límite de párrafo; colapsarlo habría degradado el chunking a una ventana deslizante pura sin
  avisar), colapsa espacios/tabs horizontales y recorta. Separa en párrafos, fusiona (`greedy merge`)
  párrafos pequeños consecutivos hasta acercarse a `app.rag.chunking.chunk-size-chars` (default
  1800, con margen bajo el límite de 8000 del CHECK), y solo sub-trocea con ventana deslizante +
  solapamiento (`overlap-chars`, default 200, corte en el espacio en blanco más cercano) los
  párrafos que siguen excediendo el tamaño. Opera **por cada `ExtractedPage` de forma
  independiente** — un chunk nunca cruza un límite de página/sección, así que siempre tiene un
  `pageNumber`/`sectionLabel` único y correcto para las futuras citas; `chunk_index` es un contador
  global monótono creciente sobre todo el documento. Aborta con `too_many_chunks` si se supera
  `max-chunks-per-document` (default 500) y con `no_extractable_text` si el resultado no produce
  ningún chunk (p. ej. un PDF escaneado sin capa de texto: no hay OCR en el alcance de este sprint).
- **Embeddings**: lotes de `app.rag.embedding.batch-size` (default 64) contra `EmbeddingProviderPort`
  con el modelo configurable `app.rag.embedding.model-id` (default `fake-embedding-v1`).
- **Indexación**: `VectorSearchPort.replaceChunks(ownerId, documentId, List<ChunkRecord>)` (nuevo
  método, ver la sección de `VectorSearchPort` arriba) — implementado en `PgVectorSearchAdapter` como
  `@Transactional`: `DELETE` de los chunks existentes del documento seguido de un `batchUpdate`
  `INSERT` (mismo patrón `BatchPreparedStatementSetter` + `PGvector` que ya usa `index()`).
  Delete-then-insert-all en vez de upsert por `chunk_index`: más simple y con la misma garantía de
  idempotencia — un reprocesamiento que produce menos chunks que el anterior no deja índices
  huérfanos. Verificado contra Postgres real que un fallo a mitad del batch (p. ej. un `chunk_index`
  duplicado) revierte también el `DELETE` (atomicidad real, no solo del `INSERT`).
- **Timeout de extracción**: `app.rag.extraction.timeout-seconds` (default 30) vía
  `Future.get(timeout)` + `cancel(true)` sobre el mismo `documentProcessingExecutor` (cached pool, así
  que siempre hay un hilo disponible para el `submit` anidado). **Limitación de v1 documentada, no
  resuelta**: ni PDFBox ni POI garantizan honrar `Thread.interrupt()` en un bucle CPU-bound — el
  timeout protege al proceso general (el documento pasa a `FAILED` y el sistema sigue funcionando)
  pero no garantiza liberar el hilo subyacente ante un input adversarial específicamente craftado.
  Mitigado en la práctica por el tamaño de archivo ya acotado en upload (25 MiB) y por el chequeo de
  `max-pages` como primera línea de defensa.
- **Fallos**: cada etapa lanza `DocumentProcessingException` con un `reasonCode` específico y
  acotado — `extraction_timeout`, `extraction_failed`, `unsupported_text_encoding`,
  `too_many_pages`, `content_too_large`, `too_many_chunks`, `no_extractable_text`,
  `embedding_failed` — persistido tal cual como `failureReason` del documento; cualquier excepción no
  anticipada cae a `processing_failed`. Nunca se persiste stack trace ni contenido del documento.
  Auditoría `DOCUMENT_PROCESSED`/`DOCUMENT_PROCESSING_FAILED` (metadata acotada: `chunkCount` o
  `reasonCode`, nunca contenido).
- **Riesgo de concurrencia mitigado**: `DocumentJpaAdapter.save()` hace upsert por re-lectura — si no
  encuentra la fila, inserta una nueva. Sin guarda, borrar un documento mientras se está procesando
  podría "resucitarlo" como `READY` sin chunks reales al terminar el procesamiento. Mitigado con un
  chequeo de existencia (`findByIdAndOwnerId`) inmediatamente antes de cada guardado final (al pasar
  a `PROCESSING` y al finalizar con `READY`/`FAILED`) — no elimina el TOCTOU al 100%, pero reduce la
  ventana de riesgo de "toda la duración del procesamiento" a "el instante entre el chequeo y el
  guardado".
- **No durable**: el trigger es un `Runnable` en memoria sobre un `ExecutorService` — si el backend
  se reinicia mientras un documento está en `PROCESSING`, queda atascado ahí sin reintento
  automático. Mismo patrón de no-durabilidad ya aceptado hoy por `mcpToolOrchestrationExecutor`;
  documentado como limitación de v1, no resuelto.
- **Diferido explícitamente**: `POST /api/documents/{id}/reindex` (el `UseCase` ya deja el
  reprocesamiento idempotente y listo, pero el endpoint no aporta valor real sin un proveedor de
  embeddings real — con el fake, reprocesar el mismo contenido produce los mismos vectores) y un
  adaptador real de `EmbeddingProviderPort`.

Verificado el 2026-07-18: unitarios — `DocumentChunkTest`, `TextChunkerTest`,
`DocumentTextExtractorAdapterTest` (incluye el caso XXE craftado y el de encoding inválido),
`FakeVectorSearchAdapterTest` ampliado, `DocumentProcessingServiceTest` (camino feliz con
extracción/chunking/embeddings reales contra el fake, cada `reasonCode` de fallo, y el caso de
borrado durante el procesamiento sin resurrección), `DocumentManagementServiceTest` ampliado (el
trigger solo dispara en uploads nuevos, sin bloquear la respuesta HTTP). Integración contra Postgres
real vía Testcontainers — `PgVectorSearchAdapterIntegrationTest` ampliado (`replaceChunks`
idempotente y con rollback atómico), `DocumentControllerIntegrationTest` ampliado (sube un documento
real vía HTTP, espera a que llegue a `READY` y confirma chunks reales en `rag.document_chunk`).
`./mvnw verify` completo del backend en verde (159/159).

### Retrieval y citas (backend)

Aprobado el 2026-07-18, con una segunda luz verde explícita el mismo día tras cerrar la extracción/
chunking. **Solo backend** — el selector de documentos en el composer del chat y el panel
`/knowledge` quedan como la siguiente pieza (ver "Pendiente de aprobación" abajo).

**Retrieval es opt-in por conversación**: solo se ejecuta si el usuario seleccionó ≥1 documento para
esa conversación. Sin selección, cero llamadas a `embed()`/`search()`, cero latencia extra, el chat
se comporta exactamente igual que antes de esta tarea.

- **Selección de documentos por conversación**: tabla-hija `chat.conversation_document`
  (`conversation_id`+`document_id` como PK compuesta, `ON DELETE CASCADE` en ambos — se eligió
  explícitamente una tabla en vez de una columna `uuid[]` en `chat.conversation`, porque un array de
  Postgres no admite FK por elemento: borrar un documento no habría limpiado automáticamente su
  presencia en el array de ninguna conversación). `ChatUseCase.selectDocuments(ownerId,
  conversationId, documentIds, remoteAddress)` — nuevo endpoint `PUT
  /api/conversations/{id}/documents` (deliberadamente separado de `PUT .../selection`, que sigue
  siendo solo para proveedor/modelo: son dos ciclos de vida independientes). Valida que todos los
  `documentIds` pertenezcan al owner (404 si no, nunca 403); **no** exige `READY` al seleccionar — se
  puede seleccionar un documento aún `PROCESSING`, y el retrieval simplemente lo ignora en silencio
  turno a turno hasta que esté listo.
- **`RagRetrievalUseCase`/`RagRetrievalService`** (nuevo, cableado a mano igual que
  `DocumentProcessingUseCase`): dado `ownerId`+`documentIds`+texto de la pregunta, valida
  ownership+`READY` de los documentos (ignora en silencio los que no lo estén — un documento borrado
  o aún procesando nunca rompe el chat, solo no aporta contexto ese turno), corta **antes** de llamar
  a `embed()` si no queda ningún documento válido, embebe la pregunta con el modelo configurado
  (`app.rag.embedding.model-id`, el mismo que usa el chunking), busca `top-k`
  (`app.rag.retrieval.top-k`, default 5) acotado a esos documentos vía `VectorSearchPort.search`, y
  filtra por `score >= app.rag.retrieval.score-threshold` (default 0.5).
- **Inyección en el prompt sin tocar los adaptadores de proveedor**: ningún adaptador soporta hoy
  `role="system"` (y Anthropic usa un parámetro top-level `system` separado, no un mensaje) —
  ampliar los 7 adaptadores de proveedor para soportarlo estaba fuera de alcance de esta tarea. En
  su lugar, `ChatService.startGeneration`/`regenerate` componen el contenido recuperado y la
  pregunta en un único `ChatMessage(role="user", ...)` con un preámbulo explícito instruyendo al
  modelo a tratar los fragmentos como datos de consulta, nunca como instrucciones — **mitigación a
  nivel de prompt, documentada explícitamente como no una garantía técnica dura** (ningún LLM obedece
  instrucciones de prompt de forma infalible). Lo que se **persiste** en `chat.message` sigue siendo
  el mensaje original del usuario sin augmentar — solo el `ChatMessage` enviado al proveedor en ese
  turno lleva el bloque de contexto — para que el historial no acumule contexto en cada
  regeneración/carga futura y cada turno vuelva a recuperar en fresco. Cap defensivo
  `app.rag.retrieval.max-context-characters` (default 20000) sobre el bloque de contexto, que nunca
  recorta la pregunta real del usuario (se acota antes de anexarla, no después).
- **Citas** (`rag.message_document`, tabla ya creada en `V7` pero sin ningún caso de uso hasta
  ahora): filas `SELECTED` (`chunk_id` null — un documento estaba en el alcance de la pregunta) y
  `CITED` (`chunk_id` no null — un chunk concreto superó el umbral y se inyectó de verdad), ambas
  asociadas siempre al mensaje **asistente** de la generación (es la respuesta la que está
  fundamentada en los documentos, no la pregunta). Nuevos métodos en `ConversationRepository`:
  `recordMessageDocuments` (mismo patrón `lockConversation` que ya usa `recordToolCall`),
  `findCitationsForMessages`, `replaceSelectedDocuments`. `ChatUseCase.MessageView` gana
  `citations: List<CitationView>`, hidratadas tanto en el momento de generar (con el resultado del
  retrieval ya en memoria) como al releer el historial (`listMessages`, vía
  `VectorSearchPort.findByIds` + `DocumentRepository.findAllByIdsAndOwnerId` — mismo patrón de
  composición en la capa de aplicación que ya usa `toolCallsByMessage`, ya que `document_chunk` vive
  fuera de JPA y no admite un JOIN JPQL directo).
- **Bug de esquema corregido de paso** (migración `V8`): `rag.message_document.chunk_id` declaraba
  `ON DELETE SET NULL` en `V7`, pero el propio CHECK exige `chunk_id IS NOT NULL` cuando
  `relation='CITED'` — borrar un chunk citado habría violado su propio constraint. Era inofensivo
  mientras nada poblara `CITED` (hasta esta tarea); corregido a `ON DELETE CASCADE`. También se
  añadió `rag.message_document.score` (`double precision`, CHECK: obligatoria en `CITED`, prohibida
  en `SELECTED`) — sin esto no habría forma de reproducir el score original de una cita al releer el
  historial días después.
- **Explícitamente diferido**: full-text search híbrido (la propia spec lo condiciona con "cuando
  aporte valor"; requeriría una migración adicional con `tsvector`/GIN y un algoritmo de fusión de
  ranking), adaptador real de `EmbeddingProviderPort`, endpoint `POST /api/documents/{id}/reindex`, y
  toda la UI (selector de documentos en el composer, panel `/knowledge`).

Verificado el 2026-07-18: unitarios — `RagRetrievalServiceTest` (documento no-`READY`/no-owned
ignorado sin llamar a `embed()`, filtrado por umbral, ranking por score), `FakeVectorSearchAdapterTest`
ampliado (`search` acotado por documento, `findByIds`), `ChatServiceTest` ampliado (mensaje
augmentado solo con selección+resultados no vacíos, contenido persistido sin augmentar,
`recordMessageDocuments` con las entradas `SELECTED`/`CITED` correctas, `selectDocuments` valida
ownership, hidratación de citas en `listMessages`). Integración contra Postgres real vía
Testcontainers — `PgVectorSearchAdapterIntegrationTest` ampliado, `PostgresMigrationTest` ampliado
para `V8`, `ChatIntegrationTest` ampliado (flujo completo seleccionar documento → enviar mensaje →
citas persistidas end-to-end contra Postgres real, y que borrar un chunk citado ya no rompe el CHECK
tras el fix del FK). `./mvnw verify` completo del backend en verde (176/176).

### Bug corregido: documentos siempre reales (2026-07-19)

Al construir la UI del selector de documentos se descubrió, verificando manualmente contra el stack
de `docker compose` real (que arranca con `APP_INTEGRATIONS_MODE=fake`, el default documentado para
pruebas locales gratuitas), que **seleccionar cualquier documento en una conversación devolvía
siempre 409**. Causa: en modo `fake`, `DocumentRepository`/`VectorSearchPort` eran los adaptadores en
memoria (`FakeDocumentRepository`/`FakeVectorSearchAdapter`), así que un documento subido nunca
llegaba a existir como fila real en `rag.document` — pero `chat.conversation_document` (añadida en la
tarea de retrieval) tiene una FK real hacia `rag.document(id)`, y `ConversationRepository` siempre usa
Postgres real. La inserción de la selección violaba la FK en cuanto se intentaba. Enmascarado en
`ChatIntegrationTest` porque ese test sembraba manualmente tanto Postgres real (para la FK) como los
beans fake (para que `RagRetrievalService`/`selectDocuments` encontraran el documento) — un truco de
test que nunca podía ocurrir en la app real.

Fix: `DocumentJpaAdapter`, `FilesystemDocumentStorageAdapter` y `PgVectorSearchAdapter` pasan a estar
siempre activos (mismo `@ConditionalOnProperty` que ya tenía `FakeEmbeddingProviderAdapter` — no hay
coste ni red de por medio, solo Postgres/filesystem locales), eliminando de paso los tres beans fake
correspondientes en `ApplicationBeansConfiguration` (las clases fake siguen existiendo, usadas
directamente por tests unitarios). `ChatIntegrationTest` se simplificó para sembrar únicamente vía
JDBC real (ya no necesita el doble sembrado). Verificado manualmente contra el stack de
`docker compose` (modo fake, el default): subir un documento vía `POST /api/documents`, esperar a
`READY` por polling, seleccionar vía `PUT /api/conversations/{id}/documents` (ya no 409), enviar un
mensaje relacionado con el contenido del documento y confirmar una cita persistida en la respuesta.
`./mvnw verify` completo del backend en verde tras el fix.

## Pendiente de aprobación

- Endpoint `POST /api/documents/{id}/reindex`.
- Adaptador real de `EmbeddingProviderPort` (OpenAI embeddings u otro proveedor).
- Full-text search híbrido combinado con la búsqueda vectorial ya implementada.

Cuando cada pieza se apruebe, las pruebas deberán cubrir aislamiento por usuario, límites, archivos
maliciosos y citas reproducibles, igual que el resto del backend: sin llamadas a APIs de embeddings
de pago en la suite automatizada.
