package com.aidatachat.adapters.out.persistence.rag;

import com.aidatachat.application.port.out.VectorSearchPort;
import com.pgvector.PGvector;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

public class PgVectorSearchAdapter implements VectorSearchPort {

    private final JdbcTemplate jdbcTemplate;

    public PgVectorSearchAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
    public List<VectorMatch> search(UUID ownerId, float[] query, int topK) {
        return jdbcTemplate.query(
                """
                SELECT id, embedding <=> ? AS distance FROM rag.document_chunk
                WHERE owner_id = ? ORDER BY embedding <=> ? LIMIT ?
                """,
                (rs, rowNum) ->
                        new VectorMatch(
                                rs.getObject("id", UUID.class), 1.0 - rs.getDouble("distance")),
                new PGvector(query),
                ownerId,
                new PGvector(query),
                topK);
    }

    @Override
    public void deleteByDocument(UUID ownerId, UUID documentId) {
        jdbcTemplate.update(
                "DELETE FROM rag.document_chunk WHERE owner_id = ? AND document_id = ?",
                ownerId,
                documentId);
    }
}
