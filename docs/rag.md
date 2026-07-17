# RAG

## Estado (Sprint 5, iniciado 2026-07-17)

El propietario del proyecto aprobó arrancar el Sprint 5 explícitamente el 2026-07-17, empezando por
el esquema de base de datos y `EmbeddingProviderPort`. Todavía no existen endpoints de upload,
extracción, chunking, recuperación ni citas: ningún documento se procesa y ninguna API de
embeddings real se invoca. El resto de este sprint (upload, `DocumentStoragePort` real,
`VectorSearchPort` real, retrieval, citas y el panel `/knowledge`) sigue sin aprobar y no debe
iniciarse sin luz verde explícita — ver `TASKS.md`.

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

## Pendiente de aprobación

- `DocumentStoragePort` y `VectorSearchPort` (ya reservados como interfaces mínimas) sin adaptador
  real ni fake.
- `DocumentRepository` real sobre `rag.document`.
- Upload con validación de tamaño, MIME real, magic bytes, nombre generado por UUID, protección
  ZIP-bomb/XXE, timeout de extracción, límites de páginas/caracteres/chunks, antivirus opcional.
- Extracción, normalización y chunking para PDF, DOCX, TXT, Markdown, CSV, JSON.
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
