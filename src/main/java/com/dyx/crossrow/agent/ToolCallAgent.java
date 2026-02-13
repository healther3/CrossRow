package com.dyx.crossrow.agent;

import cn.hutool.core.util.StrUtil;
import com.dyx.crossrow.agent.model.AgentState;
import com.dyx.crossrow.agent.model.ToolChoice;
import com.dyx.crossrow.tool.SimpleToolCallManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Data
public class ToolCallAgent extends ReActAgent{

    private List<AssistantMessage.ToolCall> pendingToolCalls = new ArrayList<>();
    private ChatResponse toolCallResponse;
    private final ToolCallback[] toolCallbacks;
    private final List<String> specialToolNames;
    private final SimpleToolCallManager toolCallingManager;
    private final ToolChoice toolChoice;

    public ToolCallAgent(ToolCallback[] toolCallbacks, List<String> specialToolNames,
                         SimpleToolCallManager toolCallingManager, ToolChoice toolChoice)
    {
        super();
        this.toolCallbacks = toolCallbacks;
        this.specialToolNames = specialToolNames;
        this.toolCallingManager = toolCallingManager;
        this.toolChoice = toolChoice;
    }

    /**
     *  executing thinking process
     * @return Whether the agent should act in the next step or not
     */
    @Override
    public boolean thinking() {
        List<Message> currentMessages = new ArrayList<>(getMessageList());
        // add user prompt into message list
        if (StrUtil.isNotBlank(getNextStepPrompt())){
            currentMessages.add(new UserMessage(getNextStepPrompt()));
        }

        try {
            // load concatenated message list
            Prompt prompt = new Prompt(currentMessages);

// ==========================================
            // 🔥 修改点 1：夺回工具执行权
            // ==========================================
            ChatResponse response = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .advisors(spec -> spec.param("userId", this.getUserId()))
                    .toolCallbacks(toolCallbacks)
                    // 开启 Proxy 模式：阻止 Spring AI 自动执行工具，强迫它立即 return ToolCall 给我们
                    .options(VertexAiGeminiChatOptions.builder()
                            .internalToolExecutionEnabled( false)
                            .build())
                    .call()
                    .chatResponse();

            AssistantMessage assistantMessage = response.getResult().getOutput();

            // ==========================================
            // 🔥 修改点 2：防并发截流器（篡改 AI 记忆）
            // ==========================================
            if (assistantMessage.hasToolCalls() && assistantMessage.getToolCalls().size() > 1) {
                log.warn(" 试图一次性调用 {} 个工具。强行物理切断，仅保留第一个",
                        assistantMessage.getToolCalls().size());

                // 只取第一个动作（比如：第一次搜索）
                AssistantMessage.ToolCall firstToolCall = assistantMessage.getToolCalls().get(0);

                // 重构 AssistantMessage，让模型和框架都认为刚才只发生了一次调用
                String content = assistantMessage.getText() != null ? assistantMessage.getText() : "";
                assistantMessage = AssistantMessage.builder()
                        .content(content)
                        .toolCalls(List.of(firstToolCall))
                        .build();

                // 更新 Response，确保给到后续 act() 方法的只有这一个工具
                org.springframework.ai.chat.model.Generation newGeneration =
                        new org.springframework.ai.chat.model.Generation(assistantMessage, response.getResult().getMetadata());
                response = new ChatResponse(List.of(newGeneration), response.getMetadata());
            }

            // save response for acting
            this.toolCallResponse = response;

            // add LLM info into message list(memory)
            //AssistantMessage assistantMessage = response.getResult().getOutput();
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
                if (StrUtil.isNotBlank(content)) {
                    setState(AgentState.FINISHED);
                }
                return false;
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
            VertexAiGeminiChatOptions options =
                    VertexAiGeminiChatOptions.builder()
                            .toolCallbacks(this.toolCallbacks)
                            .build();

            // 把带着工具名册的 Options 塞进 Prompt
            Prompt toolPrompt = new Prompt(getMessageList());
            //ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(toolPrompt, toolCallResponse);
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(toolPrompt, toolCallResponse);
            // add tool execution messages to message list
            setMessageList(toolExecutionResult.conversationHistory());
            ToolResponseMessage toolResponseMessage = (ToolResponseMessage) toolExecutionResult.conversationHistory().getLast();

            // check if agent should terminate
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
