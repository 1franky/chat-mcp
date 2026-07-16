package com.aidatachat.application.port.in;

import com.aidatachat.domain.model.MessageRole;
import com.aidatachat.domain.model.MessageStatus;
import com.aidatachat.domain.model.ProviderType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;

public interface ChatUseCase {

    ConversationPageView listConversations(UUID ownerId, String query, int page, int size);

    ConversationView getConversation(UUID ownerId, UUID conversationId);

    ConversationView createConversation(UUID ownerId, CreateConversationCommand command);

    ConversationView renameConversation(
            UUID ownerId, UUID conversationId, String title, String remoteAddress);

    ConversationView selectModel(
            UUID ownerId,
            UUID conversationId,
            UUID providerConnectionId,
            String modelId,
            String remoteAddress);

    void deleteConversation(UUID ownerId, UUID conversationId, String remoteAddress);

    List<MessageView> listMessages(UUID ownerId, UUID conversationId);

    GenerationSession startGeneration(
            UUID ownerId, UUID conversationId, String content, String remoteAddress);

    GenerationSession regenerate(
            UUID ownerId, UUID conversationId, UUID sourceMessageId, String remoteAddress);

    void cancelGeneration(
            UUID ownerId, UUID conversationId, UUID generationId, String remoteAddress);

    record CreateConversationCommand(
            String title, UUID providerConnectionId, String modelId, String remoteAddress) {}

    record ConversationView(
            UUID id,
            String title,
            UUID providerConnectionId,
            String modelId,
            Instant createdAt,
            Instant updatedAt) {}

    record ConversationPageView(
            List<ConversationView> items, int page, int size, long totalElements, int totalPages) {

        public ConversationPageView {
            items = List.copyOf(items);
        }
    }

    record MessageView(
            UUID id,
            UUID conversationId,
            long position,
            MessageRole role,
            String content,
            UUID providerConnectionId,
            ProviderType providerType,
            String modelId,
            MessageStatus status,
            Integer inputTokens,
            Integer outputTokens,
            String finishReason,
            String providerRequestId,
            UUID regeneratedFromMessageId,
            Instant createdAt,
            Instant updatedAt,
            List<ToolCallView> toolCalls) {

        public MessageView {
            toolCalls = List.copyOf(toolCalls);
        }
    }

    record ToolCallView(
            UUID id,
            String toolName,
            int generationRound,
            int sequence,
            String status,
            Map<String, Object> arguments,
            Map<String, Object> result,
            Boolean isError,
            String errorCode) {}

    record GenerationSession(UUID generationId, Flow.Publisher<GenerationEvent> events) {}

    record GenerationEvent(
            String type,
            UUID generationId,
            MessageView userMessage,
            MessageView assistantMessage,
            String delta,
            String errorCode,
            boolean retryable,
            ToolCallView toolCall) {

        public static GenerationEvent started(
                UUID generationId, MessageView userMessage, MessageView assistantMessage) {
            return new GenerationEvent(
                    "generation",
                    generationId,
                    userMessage,
                    assistantMessage,
                    null,
                    null,
                    false,
                    null);
        }

        public static GenerationEvent delta(
                UUID generationId, MessageView assistantMessage, String delta) {
            return new GenerationEvent(
                    "delta", generationId, null, assistantMessage, delta, null, false, null);
        }

        public static GenerationEvent terminal(
                String type,
                UUID generationId,
                MessageView assistantMessage,
                String errorCode,
                boolean retryable) {
            return new GenerationEvent(
                    type, generationId, null, assistantMessage, null, errorCode, retryable, null);
        }

        public static GenerationEvent toolCallRequested(
                UUID generationId, MessageView assistantMessage, ToolCallView toolCall) {
            return new GenerationEvent(
                    "tool_call", generationId, null, assistantMessage, null, null, false, toolCall);
        }

        public static GenerationEvent toolCallCompleted(
                UUID generationId, MessageView assistantMessage, ToolCallView toolCall) {
            return new GenerationEvent(
                    "tool_result",
                    generationId,
                    null,
                    assistantMessage,
                    null,
                    null,
                    false,
                    toolCall);
        }
    }
}
