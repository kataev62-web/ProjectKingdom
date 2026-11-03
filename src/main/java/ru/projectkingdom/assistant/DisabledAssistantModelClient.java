package ru.projectkingdom.assistant;

import java.util.concurrent.CompletableFuture;

/** Free default: no provider, credentials or network request. */
public final class DisabledAssistantModelClient implements AssistantModelClient {
    @Override public boolean enabled() { return false; }

    @Override public CompletableFuture<ModelResponse> propose(ModelRequest request) {
        return CompletableFuture.failedFuture(
                new IllegalStateException("assistant model provider is disabled"));
    }
}
