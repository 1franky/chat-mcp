package com.aidatachat.adapters.out.persistence.chat;

import com.aidatachat.domain.model.ConversationMessage;
import com.aidatachat.domain.model.MessageRole;
import com.aidatachat.domain.model.MessageStatus;
import com.aidatachat.domain.model.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message", schema = "chat")
class ConversationMessageEntity {

    @Id private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private long position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "provider_connection_id")
    private UUID providerConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", length = 32)
    private ProviderType providerType;

    @Column(name = "model_id", length = 255)
    private String modelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageStatus status;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "finish_reason", length = 120)
    private String finishReason;

    @Column(name = "provider_request_id", length = 200)
    private String providerRequestId;

    @Column(name = "regenerated_from_message_id")
    private UUID regeneratedFromMessageId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationMessageEntity() {}

    ConversationMessageEntity(ConversationMessage message) {
        this.id = message.id();
        this.conversationId = message.conversationId();
        this.position = message.position();
        this.role = message.role();
        this.content = message.content();
        this.providerConnectionId = message.providerConnectionId();
        this.providerType = message.providerType();
        this.modelId = message.modelId();
        this.status = message.status();
        this.inputTokens = message.inputTokens();
        this.outputTokens = message.outputTokens();
        this.finishReason = message.finishReason();
        this.providerRequestId = message.providerRequestId();
        this.regeneratedFromMessageId = message.regeneratedFromMessageId();
        this.createdAt = message.createdAt();
        this.updatedAt = message.updatedAt();
    }

    void updateAssistant(
            String content,
            MessageStatus status,
            Integer inputTokens,
            Integer outputTokens,
            String finishReason,
            String providerRequestId,
            Instant updatedAt) {
        this.content = content;
        this.status = status;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.finishReason = finishReason;
        this.providerRequestId = providerRequestId;
        this.updatedAt = updatedAt;
    }

    ConversationMessage toDomain() {
        return new ConversationMessage(
                id,
                conversationId,
                position,
                role,
                content,
                providerConnectionId,
                providerType,
                modelId,
                status,
                inputTokens,
                outputTokens,
                finishReason,
                providerRequestId,
                regeneratedFromMessageId,
                version,
                createdAt,
                updatedAt);
    }
}
