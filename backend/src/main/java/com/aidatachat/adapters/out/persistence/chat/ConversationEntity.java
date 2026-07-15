package com.aidatachat.adapters.out.persistence.chat;

import com.aidatachat.domain.model.Conversation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation", schema = "chat")
class ConversationEntity {

    @Id private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "provider_connection_id")
    private UUID providerConnectionId;

    @Column(name = "model_id", nullable = false, length = 255)
    private String modelId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationEntity() {}

    ConversationEntity(Conversation conversation) {
        this.id = conversation.id();
        this.ownerId = conversation.ownerId();
        this.createdAt = conversation.createdAt();
        update(conversation);
    }

    void update(Conversation conversation) {
        this.title = conversation.title();
        this.providerConnectionId = conversation.providerConnectionId();
        this.modelId = conversation.modelId();
        this.updatedAt = conversation.updatedAt();
    }

    void touch(Instant timestamp) {
        this.updatedAt = timestamp;
    }

    Conversation toDomain() {
        return new Conversation(
                id, ownerId, title, providerConnectionId, modelId, version, createdAt, updatedAt);
    }
}
