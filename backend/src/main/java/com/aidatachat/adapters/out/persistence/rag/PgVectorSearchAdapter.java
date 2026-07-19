package com.aidatachat.adapters.out.persistence.rag;

import com.aidatachat.application.port.out.VectorSearchPort;
import com.aidatachat.domain.model.DocumentChunk;
import com.pgvector.PGvector;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class PgVectorSearchAdapter implements VectorSearchPort {

    private final JdbcTemplate jdbcTemplate;

    public PgVectorSearchAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void replaceChunks(UUID ownerId, UUID documentId, List<ChunkRecord> chunks) {
        jdbcTemplate.update(
                "DELETE FROM rag.document_chunk WHERE owner_id = ? AND document_id = ?",
                ownerId,
                documentId);
        if (chunks.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO rag.document_chunk
                    (id, document_id, owner_id, chunk_index, content, page_number, section_label,
                     embedding_model_id, embedding, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ChunkRecord chunk = chunks.get(i);
                        ps.setObject(1, chunk.chunkId());
                        ps.setObject(2, documentId);
                        ps.setObject(3, ownerId);
                        ps.setInt(4, chunk.chunkIndex());
                        ps.setString(5, chunk.content());
                        if (chunk.pageNumber() != null) {
                            ps.setInt(6, chunk.pageNumber());
                        } else {
                            ps.setNull(6, Types.INTEGER);
                        }
                        ps.setString(7, chunk.sectionLabel());
                        ps.setString(8, chunk.embeddingModelId());
                        ps.setObject(9, new PGvector(chunk.embedding()));
                        ps.setTimestamp(10, Timestamp.from(chunk.createdAt()));
                    }

                    @Override
                    public int getBatchSize() {
                        return chunks.size();
                    }
                });
    }

    @Override
    public void index(UUID ownerId, UUID documentId, List<VectorRecord> vectors) {
        if (vectors.isEmpty()) {
            return;
        }
        int[] updatedCounts =
                jdbcTemplate.batchUpdate(
                        """
                        UPDATE rag.document_chunk SET embedding = ?
                        WHERE id = ? AND owner_id = ? AND document_id = ?
                        """,
                        new BatchPreparedStatementSetter() {
                            @Override
                            public void setValues(PreparedStatement ps, int i) throws SQLException {
                                VectorRecord record = vectors.get(i);
                                ps.setObject(1, new PGvector(record.embedding()));
                                ps.setObject(2, record.chunkId());
                                ps.setObject(3, ownerId);
                                ps.setObject(4, documentId);
                            }

                            @Override
                            public int getBatchSize() {
                                return vectors.size();
                            }
                        });
        for (int i = 0; i < updatedCounts.length; i++) {
            if (updatedCounts[i] != 1) {
                throw new IllegalStateException(
                        "Document chunk not found for embedding update: "
                                + vectors.get(i).chunkId());
            }
        }
    }

    @Override
    public List<VectorMatch> search(
            UUID ownerId, Collection<UUID> documentIds, float[] query, int topK) {
        UUID[] documentIdArray = documentIds.toArray(new UUID[0]);
        return jdbcTemplate.query(
                """
                SELECT id, embedding <=> ? AS distance FROM rag.document_chunk
                WHERE owner_id = ? AND document_id = ANY(?) ORDER BY embedding <=> ? LIMIT ?
                """,
                ps -> {
                    ps.setObject(1, new PGvector(query));
                    ps.setObject(2, ownerId);
                    ps.setArray(3, ps.getConnection().createArrayOf("uuid", documentIdArray));
                    ps.setObject(4, new PGvector(query));
                    ps.setInt(5, topK);
                },
                (rs, rowNum) ->
                        new VectorMatch(
                                rs.getObject("id", UUID.class), 1.0 - rs.getDouble("distance")));
    }

    @Override
    public List<DocumentChunk> findByIds(UUID ownerId, Collection<UUID> chunkIds) {
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        UUID[] chunkIdArray = chunkIds.toArray(new UUID[0]);
        return jdbcTemplate.query(
                """
                SELECT id, document_id, owner_id, chunk_index, content, page_number, section_label,
                       embedding_model_id, embedding::text AS embedding_text, created_at
                FROM rag.document_chunk
                WHERE owner_id = ? AND id = ANY(?)
                """,
                ps -> {
                    ps.setObject(1, ownerId);
                    ps.setArray(2, ps.getConnection().createArrayOf("uuid", chunkIdArray));
                },
                (rs, rowNum) ->
                        new DocumentChunk(
                                rs.getObject("id", UUID.class),
                                rs.getObject("document_id", UUID.class),
                                rs.getObject("owner_id", UUID.class),
                                rs.getInt("chunk_index"),
                                rs.getString("content"),
                                (Integer) rs.getObject("page_number"),
                                rs.getString("section_label"),
                                rs.getString("embedding_model_id"),
                                new PGvector(rs.getString("embedding_text")).toArray(),
                                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public void deleteByDocument(UUID ownerId, UUID documentId) {
        jdbcTemplate.update(
                "DELETE FROM rag.document_chunk WHERE owner_id = ? AND document_id = ?",
                ownerId,
                documentId);
    }
}
