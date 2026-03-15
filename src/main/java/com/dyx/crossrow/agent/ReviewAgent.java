package com.dyx.crossrow.agent;

import com.dyx.crossrow.model.ReviewResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ReviewAgent {

    private final ChatClient chatClient;

    public ReviewAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    public ReviewResult review(String task, String finalOutput) {

    }
}