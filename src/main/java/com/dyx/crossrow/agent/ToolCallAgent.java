package com.dyx.crossrow.agent;

import cn.hutool.core.util.StrUtil;
import com.dyx.crossrow.model.AgentState;
import com.dyx.crossrow.model.ToolChoice;
import com.dyx.crossrow.model.dto.StepResultDTO;
import com.dyx.crossrow.tool.SimpleToolCallManager;
import com.dyx.crossrow.tool.ToolCallStrategy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;

import java.util.ArrayList;
import java.util.List;
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
    private final ToolCallStrategy toolCallStrategy;
    
    // 临时保存当前步骤的思考结果，用于返回给调用方
    private transient String currentThinkingResult;
    
    // 保存当前步骤的 token 使用信息
    private transient Usage currentUsage;

    public ToolCallAgent(ToolCallback[] toolCallbacks, List<String> specialToolNames,
                         SimpleToolCallManager toolCallingManager, ToolChoice toolChoice,
                         ToolCallStrategy toolCallStrategy)
    {
        super();
        this.toolCallbacks = toolCallbacks;
        this.specialToolNames = specialToolNames;
        this.toolCallingManager = toolCallingManager;
        this.toolChoice = toolChoice;
        this.toolCallStrategy = toolCallStrategy;
    }
    
    @Override
    protected String getThinkingResult() {
        return this.currentThinkingResult;
    }
    
    @Override
    protected List<StepResultDTO.ToolCallInfo> getPendingToolCallInfos() {
        if (pendingToolCalls == null || pendingToolCalls.isEmpty()) {
            return null;
        }
        return pendingToolCalls.stream()
                .map(tc -> StepResultDTO.ToolCallInfo.builder()
                        .toolName(tc.name())
                        .arguments(tc.arguments())
                        .status("pending")
                        .build())
                .collect(Collectors.toList());
    }
    
    @Override
    protected Usage getCurrentUsage() {
        return this.currentUsage;
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

        // 检查消息列表是否为空，避免发送空请求给 Gemini API
        if (currentMessages.isEmpty()) {
            log.error("Message list is empty, cannot send request to LLM");
            return false;
        }

        // 记录当前消息列表状态，便于调试
        log.debug("Current messages count: {}, types: {}", 
                currentMessages.size(),
                currentMessages.stream().map(m -> m.getClass().getSimpleName()).toList());

        try {
            // load concatenated message list
            Prompt prompt = new Prompt(currentMessages);
            // enable customized tool call
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

            // get message from llm, make only one tool being called a time
            AssistantMessage assistantMessage = response.getResult().getOutput();
            assistantMessage = toolCallStrategy.processOneToolCalls(assistantMessage, response);

                // 更新 Response，确保给到后续 act() 方法的只有这一个工具
                Generation newGeneration = new Generation(assistantMessage, response.getResult().getMetadata());
                response = new ChatResponse(List.of(newGeneration), response.getMetadata());

            // save response for acting
            this.toolCallResponse = response;
            
            // 保存 token 使用信息
            if (response.getMetadata() != null) {
                this.currentUsage = response.getMetadata().getUsage();
                if (this.currentUsage != null) {
                    log.info("Token usage - prompt: {}, completion: {}, total: {}",
                            currentUsage.getPromptTokens(),
                            currentUsage.getCompletionTokens(),
                            currentUsage.getTotalTokens());
                }
            }

            // add LLM info into message list(memory)
            //AssistantMessage assistantMessage = response.getResult().getOutput();
            getMessageList().add(assistantMessage);

            // get pending tool calls
            pendingToolCalls = assistantMessage.getToolCalls();

            //load text result content
            AssistantMessage output = response.getResult().getOutput();
            String content = output.getText();
            if (content == null) content = "";
            
            // 调试日志：查看完整的 LLM 输出
            log.info("=== LLM Output Debug ===");
            log.info("Full output object: {}", output);
            log.info("Text content: '{}'", content);
            log.info("Text content length: {}", content.length());
            log.info("Tool calls count: {}", output.getToolCalls() != null ? output.getToolCalls().size() : 0);
            log.info("Metadata: {}", output.getMetadata());
            log.info("========================");
            
            // 保存思考结果，供 step() 返回给前端
            // 如果 LLM 没有返回文本但有工具调用，生成一个描述性的思考内容
            if ((content == null || content.isEmpty()) && pendingToolCalls != null && !pendingToolCalls.isEmpty()) {
                StringBuilder thinkingBuilder = new StringBuilder("Deciding to use tool: ");
                for (int i = 0; i < pendingToolCalls.size(); i++) {
                    AssistantMessage.ToolCall tc = pendingToolCalls.get(i);
                    if (i > 0) thinkingBuilder.append(", ");
                    thinkingBuilder.append(tc.name());
                }
                content = thinkingBuilder.toString();
                log.info("Generated thinking from tool calls: {}", content);
            }
            this.currentThinkingResult = content;
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
            // save error message
            this.currentThinkingResult = "Error: " + e.getMessage();
            // get error message
            String errorFeedback = String.format(
                    "SYSTEM FEEDBACK: The tool execution failed with error: '%s'. " +
                            "Please analyze this error. Did you miss a required parameter? Was the format wrong? " +
                            "Correct your approach and try calling the tool again in the next step.",
                    e.getMessage()
            );
            // put message in list
            getMessageList().add(new UserMessage(errorFeedback));
            return true;
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

            // check if agent should terminate or wait for user input
            boolean calledAskHuman = toolResponseMessage.getResponses().stream()
                    .anyMatch(response -> "askhuman".equalsIgnoreCase(response.name()));
            boolean calledTerminate = toolResponseMessage.getResponses().stream()
                    .anyMatch(response -> "terminate".equalsIgnoreCase(response.name()));
            
            if (calledAskHuman) {
                // askHuman: 暂停循环，等待用户输入
                setState(AgentState.WAITING_FOR_INPUT);
            } else if (calledTerminate) {
                // terminate: 任务完成，结束循环
                setState(AgentState.FINISHED);
            }
            // return the collected tool calling result
            String results = toolResponseMessage.getResponses().stream()
                    .map(response -> "tool:  " + response.name() + "  result: \n" + response.responseData())
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
