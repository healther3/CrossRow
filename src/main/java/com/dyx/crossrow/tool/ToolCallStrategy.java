package com.dyx.crossrow.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolCallStrategy {
    private static final Logger log = LoggerFactory.getLogger(ToolCallStrategy.class);

    public AssistantMessage processOneToolCalls(AssistantMessage original, ChatResponse response) {
        if (original.hasToolCalls() && original.getToolCalls().size() > 1) {
            log.info("Stop multiple tool callings");
            return AssistantMessage.builder()
                    .content(original.getText() != null ? original.getText() : "")
                    .toolCalls(List.of(original.getToolCalls().getFirst()))
                    .build();
        }
        return original;
    }
}
