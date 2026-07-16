package com.aidatachat.adapters.out.persistence.chat;

import com.aidatachat.domain.model.ConversationToolCall;
import com.aidatachat.domain.model.MessageToolCallStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "message_tool_call", schema = "chat")
class ConversationMessageToolCallEntity {

    @Id private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "generation_round", nullable = false)
    private short generationRound;

    @Column(nullable = false)
    private short sequence;

    @Column(name = "tool_name", nullable = false, length = 200)
    private String toolName;

    @Column(name = "provider_tool_call_id", length = 200)
    private String providerToolCallId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> arguments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageToolCallStatus status;

    @Column(name = "is_error")
    private Boolean isError;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "error_code", length = 120)
    private String errorCode;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationMessageToolCallEntity() {}

    ConversationMessageToolCallEntity(
            UUID id,
            UUID messageId,
            int generationRound,
            int sequence,
            String toolName,
            String providerToolCallId,
            Map<String, Object> arguments,
            MessageToolCallStatus status,
            Instant startedAt,
            Instant createdAt) {
        this.id = id;
        this.messageId = messageId;
        this.generationRound = (short) generationRound;
        this.sequence = (short) sequence;
        this.toolName = toolName;
        this.providerToolCallId = providerToolCallId;
        this.arguments = Map.copyOf(arguments);
        this.status = status;
        this.startedAt = startedAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    UUID getMessageId() {
        return messageId;
    }

    void updateResult(
            MessageToolCallStatus status,
            Boolean isError,
            Map<String, Object> result,
            String errorCode,
            Instant completedAt) {
        this.status = status;
        this.isError = isError;
        this.result = result == null ? null : Map.copyOf(result);
        this.errorCode = errorCode;
        this.completedAt = completedAt;
        this.updatedAt = completedAt;
    }

    ConversationToolCall toDomain() {
        return new ConversationToolCall(
                id,
                messageId,
                generationRound,
                sequence,
                toolName,
                providerToolCallId,
                arguments,
                status,
                isError,
                result,
                errorCode,
                startedAt,
                completedAt,
                createdAt,
                updatedAt);
    }
}
