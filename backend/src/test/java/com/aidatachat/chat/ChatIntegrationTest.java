package com.aidatachat.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aidatachat.adapters.out.security.AuthenticatedUser;
import com.aidatachat.application.exception.ChatConflictException;
import com.aidatachat.application.exception.ConversationNotFoundException;
import com.aidatachat.application.port.in.ChatUseCase;
import com.aidatachat.application.port.in.ChatUseCase.CreateConversationCommand;
import com.aidatachat.application.port.in.ChatUseCase.GenerationEvent;
import com.aidatachat.application.port.in.ChatUseCase.GenerationSession;
import com.aidatachat.application.port.in.IdentityUseCase;
import com.aidatachat.application.port.in.IdentityUseCase.RegisterCommand;
import com.aidatachat.application.port.in.ProviderManagementUseCase;
import com.aidatachat.application.port.in.ProviderManagementUseCase.SaveProviderCommand;
import com.aidatachat.application.port.out.ConversationRepository;
import com.aidatachat.domain.model.ConversationToolCall;
import com.aidatachat.domain.model.MessageStatus;
import com.aidatachat.domain.model.MessageToolCallStatus;
import com.aidatachat.domain.model.ProviderType;
import com.aidatachat.domain.model.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        properties =
                "app.providers.credential-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class ChatIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.2-pg18-trixie")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ai_data_chat_chat_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired private IdentityUseCase identity;
    @Autowired private ProviderManagementUseCase providers;
    @Autowired private ChatUseCase chat;
    @Autowired private ConversationRepository conversations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM chat.message_tool_call");
        jdbc.update("DELETE FROM chat.message");
        jdbc.update("DELETE FROM chat.conversation");
        jdbc.update("DELETE FROM chat.provider_model");
        jdbc.update("DELETE FROM chat.provider_connection");
        jdbc.update("DELETE FROM identity.spring_session_attributes");
        jdbc.update("DELETE FROM identity.spring_session");
        jdbc.update("DELETE FROM audit.security_audit_event");
        jdbc.update("DELETE FROM identity.app_user");
        mockMvc =
                MockMvcBuilders.webAppContextSetup(webApplicationContext)
                        .apply(springSecurity())
                        .build();
    }

    @Test
    void persistsStreamingResponseUsageAndProviderSnapshot() throws Exception {
        UserAccount owner = register("owner@example.test", "Owner");
        ProviderSelection selection = fakeProvider(owner);
        ChatUseCase.ConversationView conversation = createConversation(owner, selection);
        EventSubscriber subscriber = new EventSubscriber();

        chat.startGeneration(owner.id(), conversation.id(), "Hola", "127.0.0.1")
                .events()
                .subscribe(subscriber);

        assertThat(subscriber.terminal.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(subscriber.failure).isNull();
        assertThat(subscriber.events)
                .extracting(GenerationEvent::type)
                .containsExactly("generation", "delta", "delta", "delta", "complete");
        assertThat(chat.listMessages(owner.id(), conversation.id()))
                .hasSize(2)
                .last()
                .satisfies(
                        message -> {
                            assertThat(message.content())
                                    .isEqualTo("Respuesta simulada por fake-deterministic.");
                            assertThat(message.status()).isEqualTo(MessageStatus.COMPLETED);
                            assertThat(message.providerConnectionId()).isEqualTo(selection.id());
                            assertThat(message.providerType()).isEqualTo(ProviderType.FAKE);
                            assertThat(message.modelId()).isEqualTo("fake-chat-v1");
                            assertThat(message.inputTokens()).isEqualTo(2);
                            assertThat(message.outputTokens()).isEqualTo(7);
                            assertThat(message.providerRequestId()).isEqualTo("fake-request");
                        });
    }

    @Test
    void cancellationPersistsPartialAndCompletesTheEventStream() throws Exception {
        UserAccount owner = register("owner@example.test", "Owner");
        ProviderSelection selection = fakeProvider(owner);
        ChatUseCase.ConversationView conversation = createConversation(owner, selection);
        EventSubscriber subscriber = new EventSubscriber();
        GenerationSession session =
                chat.startGeneration(owner.id(), conversation.id(), "Detente", "127.0.0.1");
        session.events().subscribe(subscriber);

        assertThat(subscriber.firstDelta.await(2, TimeUnit.SECONDS)).isTrue();
        chat.cancelGeneration(owner.id(), conversation.id(), session.generationId(), "127.0.0.1");

        assertThat(subscriber.terminal.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(subscriber.events)
                .extracting(GenerationEvent::type)
                .contains("cancelled")
                .doesNotContain("complete");
        assertThat(chat.listMessages(owner.id(), conversation.id()).getLast())
                .satisfies(
                        message -> {
                            assertThat(message.status()).isEqualTo(MessageStatus.CANCELLED);
                            assertThat(message.content()).isEqualTo("Respuesta ");
                        });
    }

    @Test
    void ownershipIsAppliedInRepositoryQueriesAndApiResponses() throws Exception {
        UserAccount owner = register("owner@example.test", "Owner");
        UserAccount other = register("other@example.test", "Other");
        ChatUseCase.ConversationView conversation = createConversation(owner, fakeProvider(owner));

        assertThatThrownBy(() -> chat.getConversation(other.id(), conversation.id()))
                .isInstanceOf(ConversationNotFoundException.class);
        assertThat(chat.listConversations(other.id(), "", 0, 30).items()).isEmpty();
        mockMvc.perform(
                        get("/api/conversations/{id}", conversation.id())
                                .with(user(principal(other))))
                .andExpect(status().isNotFound());
    }

    @Test
    void toolCallsDoNotBypassTheSingleActiveGenerationInvariant() {
        UserAccount owner = register("owner@example.test", "Owner");
        ProviderSelection selection = fakeProvider(owner);
        ChatUseCase.ConversationView conversation = createConversation(owner, selection);
        Instant now = Instant.now();

        ConversationRepository.GenerationMessages generation =
                conversations.createGeneration(
                        conversation.id(),
                        owner.id(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Hola",
                        selection.id(),
                        ProviderType.FAKE,
                        selection.modelId(),
                        now);
        UUID toolCallId = UUID.randomUUID();
        conversations.recordToolCall(
                conversation.id(),
                owner.id(),
                generation.assistantMessage().id(),
                toolCallId,
                1,
                0,
                "health_check",
                "call-1",
                Map.of("x", 1),
                MessageToolCallStatus.RUNNING,
                now);

        assertThatThrownBy(
                        () ->
                                conversations.createGeneration(
                                        conversation.id(),
                                        owner.id(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "Otro mensaje",
                                        selection.id(),
                                        ProviderType.FAKE,
                                        selection.modelId(),
                                        now))
                .isInstanceOf(ChatConflictException.class);

        conversations.updateToolCallResult(
                conversation.id(),
                owner.id(),
                toolCallId,
                MessageToolCallStatus.COMPLETED,
                false,
                Map.of("status", "ok"),
                null,
                now);
        List<ConversationToolCall> toolCalls =
                conversations
                        .findToolCallsForMessages(List.of(generation.assistantMessage().id()))
                        .getOrDefault(generation.assistantMessage().id(), List.of());
        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.getFirst().status()).isEqualTo(MessageToolCallStatus.COMPLETED);
        assertThat(toolCalls.getFirst().isError()).isFalse();
    }

    @Test
    void enforcesUniqueRoundAndSequencePerMessage() {
        UserAccount owner = register("owner@example.test", "Owner");
        ProviderSelection selection = fakeProvider(owner);
        ChatUseCase.ConversationView conversation = createConversation(owner, selection);
        Instant now = Instant.now();
        ConversationRepository.GenerationMessages generation =
                conversations.createGeneration(
                        conversation.id(),
                        owner.id(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Hola",
                        selection.id(),
                        ProviderType.FAKE,
                        selection.modelId(),
                        now);
        conversations.recordToolCall(
                conversation.id(),
                owner.id(),
                generation.assistantMessage().id(),
                UUID.randomUUID(),
                1,
                0,
                "health_check",
                null,
                Map.of(),
                MessageToolCallStatus.PENDING,
                now);

        assertThatThrownBy(
                        () ->
                                conversations.recordToolCall(
                                        conversation.id(),
                                        owner.id(),
                                        generation.assistantMessage().id(),
                                        UUID.randomUUID(),
                                        1,
                                        0,
                                        "hello_world",
                                        null,
                                        Map.of(),
                                        MessageToolCallStatus.PENDING,
                                        now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ProviderSelection fakeProvider(UserAccount owner) {
        ProviderManagementUseCase.ProviderConnectionView connection =
                providers.createConnection(
                        owner.id(),
                        new SaveProviderCommand(
                                "Fake local",
                                ProviderType.FAKE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "127.0.0.1"));
        providers.synchronizeModels(owner.id(), connection.id(), "127.0.0.1");
        providers.selectDefaultModel(owner.id(), connection.id(), "fake-chat-v1");
        return new ProviderSelection(connection.id(), "fake-chat-v1");
    }

    private ChatUseCase.ConversationView createConversation(
            UserAccount owner, ProviderSelection selection) {
        return chat.createConversation(
                owner.id(),
                new CreateConversationCommand(
                        "Conversacion de prueba",
                        selection.id(),
                        selection.modelId(),
                        "127.0.0.1"));
    }

    private UserAccount register(String email, String displayName) {
        return identity.register(
                new RegisterCommand(
                        email, displayName, "correct-horse-battery-staple", "127.0.0.1"));
    }

    private AuthenticatedUser principal(UserAccount account) {
        return new AuthenticatedUser(
                account.id(),
                account.email(),
                account.normalizedEmail(),
                account.displayName(),
                account.passwordHash(),
                account.role(),
                account.active());
    }

    private record ProviderSelection(java.util.UUID id, String modelId) {}

    private static final class EventSubscriber implements Flow.Subscriber<GenerationEvent> {

        private final List<GenerationEvent> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch firstDelta = new CountDownLatch(1);
        private final CountDownLatch terminal = new CountDownLatch(1);
        private Throwable failure;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(GenerationEvent item) {
            events.add(item);
            if ("delta".equals(item.type())) {
                firstDelta.countDown();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
            terminal.countDown();
        }

        @Override
        public void onComplete() {
            terminal.countDown();
        }
    }
}
