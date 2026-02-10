package com.dyx.crossrow.agent;

import cn.hutool.core.util.StrUtil;
import com.dyx.crossrow.agent.model.ToolChoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ToolCallAgent extends ReActAgent{

    private List<AssistantMessage.ToolCall> pendingToolCalls = new ArrayList<>();
    private final Map<String, Object> availableTools = new HashMap<>();
    private final ToolCallback[] toolCallbacks;
    private final List<String> specialToolNames;
    private final ToolChoice toolChoice;

    public ToolCallAgent(ToolCallback[] toolCallbacks, List<String> specialToolNames, ToolChoice toolChoice)
    {
        super();
        this.toolCallbacks = toolCallbacks;
        this.specialToolNames = specialToolNames;
        this.toolChoice = toolChoice;
    }
    @Override
    public boolean thinking() {
        // add user prompt into message list
        if (StrUtil.isNotBlank(getNextStepPrompt())){
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }

        try {
            // load concatenated message list
            List<Message> messages = getMessageList();
            Prompt prompt = new Prompt(messages);

            // get response from LLM
            ChatResponse response = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .tools(toolCallbacks)
                    .call()
                    .chatResponse();

            // add LLM info into message list(memory)
            AssistantMessage assistantMessage = response.getResult().getOutput();
            getMessageList().add(assistantMessage);

            // get pending tool calls
            pendingToolCalls = assistantMessage.getToolCalls();

            //load text result content
            String content = response.getResult().getOutput().getText();
            if (content == null) content = "";
            log.info("{}'s thoughts: {}", getName(), content);

            // output tool calling details
            log.info("{} selected {} tools to use", getName(),
                    pendingToolCalls != null ? pendingToolCalls.size() : 0);
            if (pendingToolCalls != null && !pendingToolCalls.isEmpty()) {
                List<String> toolNames = pendingToolCalls.stream()
                        .map(tc -> tc.name())
                        .toList();
                log.info("Tools being prepared: {}", toolNames);
            }

            // decide acting or not
            if (pendingToolCalls == null || pendingToolCalls.isEmpty()) {
                return StrUtil.isNotBlank(content);
            }
            return true;
        }catch (Exception e){
            // print error info
            log.error("{}'s thinking process encountered an error: {}", getName(), e.getMessage());
            getMessageList().add(new AssistantMessage("Error encountered while processing: " + e.getMessage()));
            return false;
        }
    }

    @Override
    public String act() {
        return "";
    }
}
