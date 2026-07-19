package com.aidatachat.web.chat;

import com.aidatachat.adapters.out.security.AuthenticatedUser;
import com.aidatachat.application.exception.InvalidCredentialsException;
import com.aidatachat.application.port.in.ChatUseCase;
import com.aidatachat.application.port.in.ChatUseCase.ConversationPageView;
import com.aidatachat.application.port.in.ChatUseCase.ConversationView;
import com.aidatachat.application.port.in.ChatUseCase.GenerationEvent;
import com.aidatachat.application.port.in.ChatUseCase.GenerationSession;
import com.aidatachat.application.port.in.ChatUseCase.MessageView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/conversations")
public class ChatController {

    private final ChatUseCase chat;
    private final ScheduledExecutorService heartbeatScheduler;
    private final long streamTimeoutMillis;
    private final long heartbeatMillis;

    public ChatController(
            ChatUseCase chat,
            ScheduledExecutorService chatHeartbeatScheduler,
            @Value("${app.chat.stream-timeout:2m}") Duration streamTimeout,
            @Value("${app.chat.heartbeat-interval:15s}") Duration heartbeatInterval) {
        this.chat = chat;
        this.heartbeatScheduler = chatHeartbeatScheduler;
        this.streamTimeoutMillis = streamTimeout.toMillis();
        this.heartbeatMillis = heartbeatInterval.toMillis();
    }

    @GetMapping
    ConversationPageView list(
            Authentication authentication,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return chat.listConversations(ownerId(authentication), query, page, size);
    }

    @GetMapping("/{conversationId}")
    ConversationView get(Authentication authentication, @PathVariable UUID conversationId) {
        return chat.getConversation(ownerId(authentication), conversationId);
    }

    @PostMapping
    ResponseEntity<ConversationView> create(
            Authentication authentication,
            @Valid @RequestBody CreateConversationRequest body,
            HttpServletRequest request) {
        ConversationView created =
                chat.createConversation(
                        ownerId(authentication), body.toCommand(request.getRemoteAddr()));
        return ResponseEntity.status(201)
                .header(HttpHeaders.LOCATION, "/api/conversations/" + created.id())
                .body(created);
    }

    @PutMapping("/{conversationId}/title")
    ConversationView rename(
            Authentication authentication,
            @PathVariable UUID conversationId,
            @Valid @RequestBody RenameConversationRequest body,
            HttpServletRequest request) {
        return chat.renameConversation(
                ownerId(authentication), conversationId, body.title(), request.getRemoteAddr());
    }

    @PutMapping("/{conversationId}/selection")
    ConversationView selectModel(
            Authentication authentication,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SelectModelRequest body,
            HttpServletRequest request) {
        return chat.selectModel(
                ownerId(authentication),
                conversationId,
                body.providerConnectionId(),
                body.modelId(),
                request.getRemoteAddr());
    }

    @PutMapping("/{conversationId}/documents")
    ConversationView selectDocuments(
            Authentication authentication,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SelectDocumentsRequest body,
            HttpServletRequest request) {
        return chat.selectDocuments(
                ownerId(authentication),
                conversationId,
                body.documentIds(),
                request.getRemoteAddr());
    }

    @DeleteMapping("/{conversationId}")
    ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable UUID conversationId,
            HttpServletRequest request) {
        chat.deleteConversation(ownerId(authentication), conversationId, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{conversationId}/messages")
    List<MessageView> messages(Authentication authentication, @PathVariable UUID conversationId) {
        return chat.listMessages(ownerId(authentication), conversationId);
    }

    @PostMapping(
            path = "/{conversationId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> stream(
            Authentication authentication,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest body,
            HttpServletRequest request) {
        GenerationSession session =
                chat.startGeneration(
                        ownerId(authentication),
                        conversationId,
                        body.content(),
                        request.getRemoteAddr());
        return emitter(session);
    }

    @PostMapping(
            path = "/{conversationId}/messages/{messageId}/regenerate/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> regenerate(
            Authentication authentication,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            HttpServletRequest request) {
        GenerationSession session =
                chat.regenerate(
                        ownerId(authentication),
                        conversationId,
                        messageId,
                        request.getRemoteAddr());
        return emitter(session);
    }

    @DeleteMapping("/{conversationId}/generations/{generationId}")
    ResponseEntity<Void> cancel(
            Authentication authentication,
            @PathVariable UUID conversationId,
            @PathVariable UUID generationId,
            HttpServletRequest request) {
        chat.cancelGeneration(
                ownerId(authentication), conversationId, generationId, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<SseEmitter> emitter(GenerationSession session) {
        SseEmitter emitter = new SseEmitter(streamTimeoutMillis);
        SseSubscriber subscriber = new SseSubscriber(emitter, heartbeatScheduler, heartbeatMillis);
        emitter.onTimeout(subscriber::cancel);
        emitter.onError(error -> subscriber.cancel());
        emitter.onCompletion(subscriber::closeHeartbeat);
        session.events().subscribe(subscriber);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    private UUID ownerId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new InvalidCredentialsException();
        }
        return principal.id();
    }

    public record CreateConversationRequest(
            @Size(max = 160) String title,
            @NotNull UUID providerConnectionId,
            @NotBlank @Size(max = 255) String modelId) {

        ChatUseCase.CreateConversationCommand toCommand(String remoteAddress) {
            return new ChatUseCase.CreateConversationCommand(
                    title, providerConnectionId, modelId, remoteAddress);
        }
    }

    public record RenameConversationRequest(@NotBlank @Size(max = 160) String title) {}

    public record SelectModelRequest(
            @NotNull UUID providerConnectionId, @NotBlank @Size(max = 255) String modelId) {}

    public record SelectDocumentsRequest(@NotNull List<UUID> documentIds) {}

    public record SendMessageRequest(@NotBlank @Size(max = 32000) String content) {}

    private static final class SseSubscriber implements Flow.Subscriber<GenerationEvent> {

        private final SseEmitter emitter;
        private final ScheduledExecutorService scheduler;
        private final long heartbeatMillis;
        private Flow.Subscription subscription;
        private ScheduledFuture<?> heartbeat;

        private SseSubscriber(
                SseEmitter emitter, ScheduledExecutorService scheduler, long heartbeatMillis) {
            this.emitter = emitter;
            this.scheduler = scheduler;
            this.heartbeatMillis = heartbeatMillis;
        }

        @Override
        public synchronized void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            heartbeat =
                    scheduler.scheduleAtFixedRate(
                            this::heartbeat,
                            heartbeatMillis,
                            heartbeatMillis,
                            TimeUnit.MILLISECONDS);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public synchronized void onNext(GenerationEvent item) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .id(item.generationId().toString())
                                .name(item.type())
                                .data(item));
            } catch (IOException exception) {
                cancel();
            }
        }

        @Override
        public synchronized void onError(Throwable throwable) {
            closeHeartbeat();
            emitter.completeWithError(throwable);
        }

        @Override
        public synchronized void onComplete() {
            closeHeartbeat();
            emitter.complete();
        }

        private synchronized void heartbeat() {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException exception) {
                cancel();
            }
        }

        private synchronized void cancel() {
            closeHeartbeat();
            if (subscription != null) {
                subscription.cancel();
            }
        }

        private synchronized void closeHeartbeat() {
            if (heartbeat != null) {
                heartbeat.cancel(false);
                heartbeat = null;
            }
        }
    }
}
