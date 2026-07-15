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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class FakeLlmProviderAdapter implements LlmProviderPort, ModelCatalogPort {

    public static final String MODEL_ID = "fake-chat-v1";
    private static final long CHUNK_DELAY_MILLIS = 35;

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
                        new LlmChunk("fake-deterministic.", true, 2, 7, "stop", "fake-request")));
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
        private final AtomicLong demand = new AtomicLong();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean started = new AtomicBoolean();

        private FiniteSubscription(
                Flow.Subscriber<? super LlmChunk> subscriber, List<LlmChunk> chunks) {
            this.subscriber = subscriber;
            this.chunks = chunks;
        }

        @Override
        public void request(long count) {
            if (cancelled.get()) {
                return;
            }
            if (count <= 0) {
                cancelled.set(true);
                subscriber.onError(new IllegalArgumentException("Demand must be positive"));
                return;
            }
            demand.updateAndGet(current -> addDemand(current, count));
            synchronized (this) {
                notifyAll();
            }
            if (started.compareAndSet(false, true)) {
                Thread.ofVirtual().name("fake-llm-stream").start(this::emit);
            }
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            synchronized (this) {
                notifyAll();
            }
        }

        private void emit() {
            for (LlmChunk chunk : chunks) {
                if (!awaitDemand()) {
                    return;
                }
                try {
                    Thread.sleep(CHUNK_DELAY_MILLIS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    cancel();
                    return;
                }
                if (cancelled.get()) {
                    return;
                }
                subscriber.onNext(chunk);
            }
            if (!cancelled.get()) {
                subscriber.onComplete();
            }
        }

        private boolean awaitDemand() {
            synchronized (this) {
                while (!cancelled.get() && demand.get() == 0) {
                    try {
                        wait();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        cancel();
                        return false;
                    }
                }
            }
            if (cancelled.get()) {
                return false;
            }
            demand.updateAndGet(current -> current == Long.MAX_VALUE ? current : current - 1);
            return true;
        }

        private long addDemand(long current, long increment) {
            if (current == Long.MAX_VALUE || increment == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            long updated = current + increment;
            return updated < 0 ? Long.MAX_VALUE : updated;
        }
    }
}
