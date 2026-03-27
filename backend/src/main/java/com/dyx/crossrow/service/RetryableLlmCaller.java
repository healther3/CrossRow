package com.dyx.crossrow.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class RetryableLlmCaller {

    @Retryable(
            retryFor = { ResourceAccessException.class, WebClientResponseException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000)
    )
    public ChatResponse callLlm(ChatClient chatClient, Prompt prompt,
                                String systemPrompt, String userId,
                                ToolCallback[] toolCallbacks) {
        return chatClient.prompt(prompt)
                .system(systemPrompt)
                .advisors(spec -> spec.param("userId", userId))
                .toolCallbacks(toolCallbacks)
                .options(VertexAiGeminiChatOptions.builder()
                        .internalToolExecutionEnabled(false)
                        .build())
                .call()
                .chatResponse();
    }

    @Recover
    public ChatResponse recoverLlmCall(Exception e, ChatClient chatClient,
                                       Prompt prompt, String systemPrompt,
                                       String userId, ToolCallback[] toolCallbacks) {
        throw new RuntimeException("LLM API tries 3 times still fail: " + e.getMessage(), e);
    }
}