package com.aidatachat.adapters.out.fake;

import com.aidatachat.application.port.out.LlmProviderPort;
import com.aidatachat.application.port.out.ModelCatalogPort;
import com.aidatachat.domain.model.CapabilityAvailability;
import com.aidatachat.domain.model.DiscoveredProviderModel;
import com.aidatachat.domain.model.IntegrationState;
import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.ModelDescriptor;
import com.aidatachat.domain.model.ProviderCapabilities;
import com.aidatachat.domain.model.ProviderCapabilityProfile;
import com.aidatachat.domain.model.ProviderDescriptor;
import com.aidatachat.domain.model.ProviderProbeResult;
import com.aidatachat.domain.model.ProviderType;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;

public final class FakeLlmProviderAdapter implements LlmProviderPort, ModelCatalogPort {

    public static final String MODEL_ID = "fake-chat-v1";

    private static final ProviderCapabilities CAPABILITIES =
            new ProviderCapabilities(true, true, false, false, false, false, true);
    private static final ProviderCapabilityProfile CAPABILITY_PROFILE =
            new ProviderCapabilityProfile(
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.SUPPORTED,
                    CapabilityAvailability.UNSUPPORTED,
                    CapabilityAvailability.UNSUPPORTED,
                    CapabilityAvailability.UNSUPPORTED,
                    CapabilityAvailability.UNSUPPORTED,
                    CapabilityAvailability.SUPPORTED);

    @Override
    public ProviderType providerType() {
        return ProviderType.FAKE;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor(
                "fake-deterministic",
                "Proveedor determinista de pruebas",
                "FAKE",
                IntegrationState.UP,
                CAPABILITIES,
                true);
    }

    @Override
    public List<ModelDescriptor> listModels() {
        return List.of(
                new ModelDescriptor(
                        MODEL_ID, "Modelo falso determinista", "CONFIGURED", CAPABILITIES));
    }

    @Override
    public ProviderCapabilityProfile capabilities(ProviderClientConfiguration configuration) {
        return CAPABILITY_PROFILE;
    }

    @Override
    public ProviderProbeResult testConnection(
            ProviderClientConfiguration configuration, char[] credential) {
        return ProviderProbeResult.success("fake-request");
    }

    @Override
    public List<DiscoveredProviderModel> discoverModels(
            ProviderClientConfiguration configuration, char[] credential) {
        return List.of(
                new DiscoveredProviderModel(
                        MODEL_ID, "Modelo falso determinista", CAPABILITY_PROFILE));
    }

    @Override
    public Flow.Publisher<LlmChunk> streamChat(LlmChatRequest request) {
        Objects.requireNonNull(request, "request is required");
        if (!MODEL_ID.equals(request.modelId())) {
            throw new IllegalArgumentException("Unknown fake model: " + request.modelId());
        }

        return new FinitePublisher(
                List.of(
                        new LlmChunk("Respuesta ", false),
                        new LlmChunk("simulada por ", false),
                        new LlmChunk("fake-deterministic.", true)));
    }

    private static final class FinitePublisher implements Flow.Publisher<LlmChunk> {

        private final List<LlmChunk> chunks;

        private FinitePublisher(List<LlmChunk> chunks) {
            this.chunks = List.copyOf(chunks);
        }

        @Override
        public void subscribe(Flow.Subscriber<? super LlmChunk> subscriber) {
            Objects.requireNonNull(subscriber, "subscriber is required");
            subscriber.onSubscribe(new FiniteSubscription(subscriber, chunks));
        }
    }

    private static final class FiniteSubscription implements Flow.Subscription {

        private final Flow.Subscriber<? super LlmChunk> subscriber;
        private final List<LlmChunk> chunks;
        private int index;
        private boolean cancelled;
        private boolean completed;

        private FiniteSubscription(
                Flow.Subscriber<? super LlmChunk> subscriber, List<LlmChunk> chunks) {
            this.subscriber = subscriber;
            this.chunks = chunks;
        }

        @Override
        public synchronized void request(long count) {
            if (cancelled || completed) {
                return;
            }
            if (count <= 0) {
                cancelled = true;
                subscriber.onError(new IllegalArgumentException("Demand must be positive"));
                return;
            }

            long remaining = count;
            while (!cancelled && remaining > 0 && index < chunks.size()) {
                subscriber.onNext(chunks.get(index++));
                remaining--;
            }
            if (!cancelled && index == chunks.size()) {
                completed = true;
                subscriber.onComplete();
            }
        }

        @Override
        public synchronized void cancel() {
            cancelled = true;
        }
    }
}
