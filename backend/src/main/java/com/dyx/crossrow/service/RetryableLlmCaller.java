package com.dyx.crossrow.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.function.Supplier;

/**
 * Centralised retry wrapper for all synchronous LLM calls.
 * Transient failures (429 / 5xx / network) are retried up to 3 times
 * with exponential back-off (1s → 2s → 4s, capped at 8s).
 */
@Slf4j
@Service
public class RetryableLlmCaller {

    /**
     * Generic retry wrapper — covers every synchronous LLM call pattern.
     * Callers pass the actual call as a lambda.
     *
     * Usage:
     * <pre>
     * ChatResponse resp = retryableLlmCaller.callWithRetry(() ->
     *     chatClient.prompt()
     *         .user(message)
     *         .call()
     *         .chatResponse()
     * );
     * </pre>
     */
    @Retryable(
            retryFor = { ResourceAccessException.class, WebClientResponseException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000)
    )
    public <T> T callWithRetry(Supplier<T> llmCall) {
        return llmCall.get();
    }

    @Recover
    public <T> T recoverGeneric(Exception e, Supplier<T> llmCall) {
        log.error("LLM API failed after 3 retries: {}", e.getMessage());
        throw new RuntimeException("LLM API failed after 3 retries: " + e.getMessage(), e);
    }
}