package com.dyx.crossrow.agent;

import cn.hutool.core.util.StrUtil;
import com.dyx.crossrow.agent.model.AgentState;
import com.dyx.crossrow.agent.model.ToolChoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ToolCallAgent extends ReActAgent{

    private List<AssistantMessage.ToolCall> pendingToolCalls = new ArrayList<>();
    private ChatResponse toolCallResponse;
    private final Map<String, Object> availableTools = new HashMap<>();
    private final ToolCallback[] toolCallbacks;
    private final List<String> specialToolNames;
    private final ToolChoice toolChoice;
    private final ToolCallingManager toolCallingManager;

    public ToolCallAgent(ToolCallback[] toolCallbacks, List<String> specialToolNames,
                         ToolCallingManager toolCallingManager,ToolChoice toolChoice)
    {
        super();
        this.toolCallbacks = toolCallbacks;
        this.specialToolNames = specialToolNames;
        this.toolChoice = toolChoice;
        this.toolCallingManager = toolCallingManager;
    }

    /**
     *  executing thinking process
     * @return Whether the agent should act in the next step or not
     */
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

            // save response for acting
            this.toolCallResponse = response;

            // add LLM info into message list(memory)
            AssistantMessage assistantMessage = response.getResult().getOutput();
            getMessageList().add(assistantMessage);

            // get pending tool calls
            pendingToolCalls = assistantMessage.getToolCalls();

            //load text result content
            String content = response.getResult().getOutput().getText();
            if (content == null) content = "";
            log.info("{}'s thoughts: {}", getName(), content);

            // output total number of tool used
            log.info("{} selected {} tools to use", getName(),
                    pendingToolCalls != null ? pendingToolCalls.size() : 0);

            //output names of tools
            if (pendingToolCalls != null && !pendingToolCalls.isEmpty()) {
                List<String> toolNames = pendingToolCalls.stream()
                        .map(tc -> tc.name())
                        .toList();
                log.info("Tools being prepared: {}", toolNames);
            }

            // decide acting or not
            if (pendingToolCalls == null || pendingToolCalls.isEmpty()) {
                // if no content then return false, else return true: output text
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

    /**
     * execute tool calls and return the result
     * @return result of tool calling
     */
    @Override
    public String act() {
        // decide whether agent need to use tool
        if (pendingToolCalls == null || pendingToolCalls.isEmpty()) {
            return "No tool calls need.";
        }

        try {
            // execute tools
            Prompt toolPrompt = new Prompt(getMessageList());
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(toolPrompt, toolCallResponse);

            // add tool execution messages to message list
            setMessageList(toolExecutionResult.conversationHistory());
            ToolResponseMessage toolResponseMessage = (ToolResponseMessage) toolExecutionResult.conversationHistory().getLast();

            // check if should terminate
            boolean shouldTerminate = toolResponseMessage.getResponses().stream()
                    .anyMatch(response -> specialToolNames.contains(response.name().toLowerCase()));
            if(shouldTerminate) {
                setState(AgentState.FINISHED);
            }
            // return the collected tool calling result
            String results = toolResponseMessage.getResponses().stream()
                    .map(response -> "tool:  " + response.name() + "  result:" + response.responseData())
                    .collect(Collectors.joining("\n"));
            log.info(results);
            return results;
        } catch (Exception e)
        {
            // print error info
            log.error("{}'s acting process encountered an error: {}", getName(), e.getMessage());
            getMessageList().add(new AssistantMessage("Error encountered while using tool calls: " + e.getMessage()));
            return "fail to use tool calling.";
        }
    }
}
