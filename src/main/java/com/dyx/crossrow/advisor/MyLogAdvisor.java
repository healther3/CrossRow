
package com.dyx.crossrow.advisor;

import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.ModelOptionsUtils;
import reactor.core.publisher.Flux;

@Slf4j
public class MyLogAdvisor implements CallAdvisor, StreamAdvisor {
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        this.logRequest(chatClientRequest);
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        this.logResponse(chatClientResponse);
        return chatClientResponse;
    }

    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        this.logRequest(chatClientRequest);
        Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);
        return (new ChatClientMessageAggregator()).aggregateChatClientResponse(chatClientResponses, this::logResponse);
    }

    protected void logRequest(ChatClientRequest request) {
        log.info("AI request: {}", request.prompt().getUserMessage().getText());
    }

    protected void logResponse(ChatClientResponse chatClientResponse) {
        log.info("AI response: {}", chatClientResponse.chatResponse().getResult().getOutput().getText());

        Usage usage = chatClientResponse.chatResponse().getMetadata().getUsage();
        if (usage != null) {
            log.info("Token usage - prompt: {}, completion: {}, total: {}",
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens());
        }    }

    public String getName() {
        return this.getClass().getSimpleName();
    }

    public int getOrder() {
        return 100;
    }

    public String toString() {
        return MyLogAdvisor.class.getSimpleName();
    }

}
