package ru.projectkingdom.assistant;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Injectable asynchronous seam for an optional external planning model. */
public interface AssistantModelClient {
    record ModelRequest(UUID requestId, String compactContext) {}
    record ModelAction(String type, String argument, List<String> rawBlocks) {}
    record ModelResponse(UUID requestId, List<ModelAction> actions, String text) {}

    boolean enabled();

    CompletableFuture<ModelResponse> propose(ModelRequest request);
}
