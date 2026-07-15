package com.aidatachat.adapters.out.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidatachat.domain.model.ChatMessage;
import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.LlmChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class FakeLlmProviderAdapterTest {

    private final FakeLlmProviderAdapter adapter = new FakeLlmProviderAdapter();

    @Test
    void returnsOnlyConfiguredFakeModel() {
        assertThat(adapter.listModels())
                .singleElement()
                .satisfies(
                        model -> {
                            assertThat(model.id()).isEqualTo(FakeLlmProviderAdapter.MODEL_ID);
                            assertThat(model.origin()).isEqualTo("CONFIGURED");
                        });
        assertThat(adapter.descriptor().fake()).isTrue();
        assertThat(adapter.descriptor().capabilities().chat()).isTrue();
        assertThat(adapter.descriptor().capabilities().streaming()).isTrue();
        assertThat(adapter.descriptor().capabilities().modelDiscovery()).isTrue();
        assertThat(adapter.descriptor().capabilities().toolCalling()).isFalse();
        assertThat(adapter.descriptor().capabilities().structuredOutput()).isFalse();
        assertThat(adapter.descriptor().capabilities().vision()).isFalse();
        assertThat(adapter.descriptor().capabilities().embeddings()).isFalse();
    }

    @Test
    void streamsAStableDeterministicResponseWithoutExternalCalls() throws InterruptedException {
        LlmChatRequest request =
                new LlmChatRequest(
                        FakeLlmProviderAdapter.MODEL_ID, List.of(new ChatMessage("user", "hola")));
        CollectingSubscriber subscriber = new CollectingSubscriber();

        adapter.streamChat(request).subscribe(subscriber);

        assertThat(subscriber.finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(subscriber.failure).isNull();
        assertThat(subscriber.completed).isTrue();
        assertThat(subscriber.chunks)
                .extracting(LlmChunk::content)
                .containsExactly("Respuesta ", "simulada por ", "fake-deterministic.");
        assertThat(subscriber.chunks.getLast().finished()).isTrue();
    }

    @Test
    void rejectsUnknownModels() {
        LlmChatRequest request =
                new LlmChatRequest("not-configured", List.of(new ChatMessage("user", "hola")));

        assertThatThrownBy(() -> adapter.streamChat(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown fake model");
    }

    private static final class CollectingSubscriber implements Flow.Subscriber<LlmChunk> {

        private final List<LlmChunk> chunks = new ArrayList<>();
        private final CountDownLatch finished = new CountDownLatch(1);
        private Throwable failure;
        private boolean completed;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(LlmChunk item) {
            chunks.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
            finished.countDown();
        }

        @Override
        public void onComplete() {
            completed = true;
            finished.countDown();
        }
    }
}
