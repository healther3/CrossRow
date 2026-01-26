
package com.dyx.crossrow.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.metadata.Usage;


@Slf4j
public class MyLogAdvisor implements BaseAdvisor {
    private final int order;

    public MyLogAdvisor() {
        this(0);
    }

    public MyLogAdvisor(int order) {
        this.order = order;
    }

    /**
     *  output AI request before processing request
     * @param request
     * @param chain
     * @return
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        log.info("AI request: {}", request.prompt().getUserMessage().getText());
        return request;
    }

    /**
     *  output AI response and total token consumed
     * @param response
     * @param chain
     * @return
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        String content = response.chatResponse().getResult().getOutput().getText();
        log.info("AI response: {}", content);

        Usage usage = response.chatResponse().getMetadata().getUsage();
        if (usage != null) {
            log.info("Token usage - prompt: {}, completion: {}, total: {}",
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens());
        }

        return response;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

}
