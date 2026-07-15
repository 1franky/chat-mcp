package com.aidatachat.application.port.out;

import java.util.List;

public interface EmbeddingProviderPort {

    List<float[]> embed(String modelId, List<String> inputs);
}
