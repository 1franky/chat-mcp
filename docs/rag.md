# RAG

## Estado (Sprint 5, iniciado 2026-07-17)

El propietario del proyecto aprobó arrancar el Sprint 5 explícitamente el 2026-07-17, empezando por
el esquema de base de datos y `EmbeddingProviderPort`; el mismo día aprobó los adaptadores reales y
fake para `DocumentRepository`, `DocumentStoragePort` y `VectorSearchPort`; y después el endpoint de
subida `POST /api/documents` con sus protecciones. Todavía no existen extracción de texto, chunking,
embeddings reales, retrieval ni citas: un documento subido queda en `UPLOADED` sin procesarse más.
El resto de este sprint (extracción, chunking, retrieval, citas y el panel `/knowledge`) sigue sin
aprobar y no debe iniciarse sin luz verde explícita — ver `TASKS.md`.

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
distintos. Se activa igual que el resto de fakes, con `app.integrations.mode=fake`
(`matchIfMissing = true`). Todavía no existe un adaptador real (OpenAI embeddings, etc.); se añadirá
cuando el propietario apruebe esa parte del sprint.

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

`VectorSearchPort` (`index`/`search`/`deleteByDocument`, ya definido en el sprint anterior) tiene
ahora `PgVectorSearchAdapter` (`adapters/out/persistence/rag/`): **JDBC nativo vía `JdbcTemplate` +
la librería `com.pgvector:pgvector`**, no JPA — Hibernate no tiene soporte nativo para el tipo
`vector` en este stack, así que este adaptador es el único punto del backend que usa SQL directo en
lugar de un repositorio Spring Data. Decisiones de diseño:
- `index()` hace `UPDATE` de la columna `embedding` sobre chunks que **ya existen** en
  `rag.document_chunk`, nunca `INSERT` — la columna es `NOT NULL` sin valor por defecto, así que la
  fila completa (contenido + vector inicial) solo puede crearse cuando exista chunking real, que es
  trabajo de un sprint futuro. Si un `chunkId` no existe, lanza `IllegalStateException` (fallo
  defensivo, sin fallback silencioso). Esto es coherente con el futuro
  `POST /api/documents/{id}/reindex` ya documentado abajo.
- `search()` ordena por distancia coseno (`<=>`) usando el índice HNSW ya creado en `V7`;
  `VectorMatch.score()` es `1 - distancia` (similitud coseno). El fake usa el mismo criterio para
  ser intercambiable.
- No se creó ningún port/repositorio adicional para el contenido no-vectorial de `document_chunk`
  ni para `rag.message_document`: ningún caso de uso los consume todavía (extracción/chunking y
  retrieval/citas siguen sin aprobar), así que hacerlo habría sido diseñar en el vacío. Por el mismo
  motivo tampoco se crearon los modelos de dominio `DocumentChunk`/`MessageDocument` — solo
  `Document`/`DocumentStatus`, que sí mapean 1:1 a un port ya implementado.

Los seis adaptadores (`DocumentJpaAdapter`, `FilesystemDocumentStorageAdapter`,
`PgVectorSearchAdapter` y sus tres fakes) son beans condicionales por `app.integrations.mode` en
`ApplicationBeansConfiguration`, igual que `EmbeddingProviderPort`/`LlmProviderPort`.

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

## Pendiente de aprobación

- Extracción, normalización y chunking para PDF, DOCX, TXT, Markdown, CSV, JSON (esto es lo que
  crearía las filas de `rag.document_chunk` que `VectorSearchPort.index()` espera encontrar; también
  cubriría XXE, límites de páginas/caracteres/chunks y timeout de extracción, diferidos arriba).
- Retrieval combinando búsqueda vectorial y full-text search, top-k/umbral configurables, citas y
  tratamiento del contenido recuperado como no confiable (ignorar instrucciones embebidas en
  documentos).
- Endpoints `GET/POST /api/documents`, `GET /api/documents/{id}`, `POST
  /api/documents/{id}/reindex`, `DELETE /api/documents/{id}`.
- Selector de documentos en el composer del chat, estado de indexación, citas en los mensajes y
  panel `/knowledge`.

Cuando cada pieza se apruebe, las pruebas deberán cubrir aislamiento por usuario, límites, archivos
maliciosos y citas reproducibles, igual que el resto del backend: sin llamadas a APIs de embeddings
de pago en la suite automatizada.
