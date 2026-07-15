package com.aidatachat.application.port.out;

import com.aidatachat.domain.model.LlmChatRequest;
import com.aidatachat.domain.model.LlmChunk;
import com.aidatachat.domain.model.ProviderDescriptor;
import java.util.concurrent.Flow;

public interface LlmProviderPort {

    ProviderDescriptor descriptor();

    Flow.Publisher<LlmChunk> streamChat(LlmChatRequest request);
}
