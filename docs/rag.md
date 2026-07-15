# RAG

RAG no está implementado en Sprint 0.

Sólo se reservan:

- el namespace PostgreSQL `rag` y la extensión `vector`;
- `EmbeddingProviderPort`, `DocumentStoragePort`, `VectorSearchPort` y `DocumentRepository`;
- el volumen nombrado `chat-documents` y variables de límite de upload.

No existen endpoints de upload, extracción, chunking, embeddings, índices vectoriales, recuperación ni citas. Ningún documento se procesa y ninguna API de embeddings se invoca.

Cuando su sprint sea aprobado, Flyway deberá crear las tablas con dimensión explícita por modelo, ownership por usuario, estado de procesamiento, trazabilidad de chunks y política de borrado. Las pruebas deberán cubrir aislamiento, límites, archivos maliciosos y citas reproducibles.
