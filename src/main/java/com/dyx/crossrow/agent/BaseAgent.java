package com.dyx.crossrow.agent;

import cn.hutool.core.util.StrUtil;
import com.dyx.crossrow.model.AgentState;
import com.dyx.crossrow.model.dto.StepResultDTO;
import com.dyx.crossrow.exceptions.AgentStateException;
import com.dyx.crossrow.exceptions.EmptyUserPromptException;
import com.dyx.crossrow.utils.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * BaseAgent class, implement agent loop:
 * execute step by step in agent loop for
 * ReAct Agent implementation
 */
@Data
@Slf4j
public abstract class BaseAgent {
    // attributes
    private String name;
    private String systemPrompt;
    private String nextStepPrompt;
    //aut
    private String userId;
    private String sessionId;
    //agent state
    private AgentState state = AgentState.IDLE;
    //restricted max step
    private int currentStep = 0;
    private int maxStep = 10;
    //LLM
    private ChatClient chatClient;
    //memory
    private List<Message> messageList = new ArrayList<>();

    /**
     * @param userPrompt user input
     * @return execution result
     */
    public String run(String userPrompt) {
        //check exceptions - 允许从 IDLE 或 WAITING_FOR_INPUT 状态启动
        if (this.state != AgentState.IDLE && this.state != AgentState.WAITING_FOR_INPUT) {
            throw new AgentStateException(this.state);
        }
        if (StrUtil.isEmpty(userPrompt)) {
            throw new EmptyUserPromptException();
        }
        // change state
        this.state = AgentState.RUNNING;
        //save context and result
        messageList.add(new UserMessage(userPrompt));

        try {
            List<String> results = new ArrayList<>();
            //execute - 在 FINISHED 或 WAITING_FOR_INPUT 时停止循环
            for (int i = 0; i < maxStep && this.state == AgentState.RUNNING; i++) {
                currentStep = i + 1;
                log.info("Step: {}/{}", currentStep, maxStep);
                String stepResult = step();
                results.add("Step: " + currentStep + ":" + stepResult);
            }

            //CHECK STATUS
            if (this.state == AgentState.WAITING_FOR_INPUT) {
                log.info("Agent waiting for user input");
            } else if (currentStep >= maxStep) {
                this.state = AgentState.FINISHED;
                results.add("Finished: reached max steps");
                log.info("Agent Finished");
            }

            return String.join("\n", results);

        } catch (Exception e) {
            this.state = AgentState.ERROR;
            log.error("Error: agent can't execute, {}", e.getMessage());
            return "Error: agent can't execute, " + e.getMessage();
        } finally {
            clean();
        }
    }

    /**
     * Single step
     *
     * @return result
     */
    public abstract String step();

    /**
     * clean resources
     * 注意：WAITING_FOR_INPUT 状态时保留 messageList，以便用户回复后继续对话
     */
    protected void clean() {
        log.debug("Cleaning agent [{}] resources, previous state: {}", this.name, this.state);

        // WAITING_FOR_INPUT 状态时保留对话历史，只重置步数
        if (this.state == AgentState.WAITING_FOR_INPUT) {
            this.currentStep = 0;
            log.debug("Agent [{}] in WAITING_FOR_INPUT state, preserving message history", this.name);
            return;
        }

        this.state = AgentState.IDLE;
        this.currentStep = 0;

        if (this.messageList != null) {
            this.messageList.clear();
        }

        log.debug("Agent [{}] cleaned successfully", this.name);
    }

    /**
     * @param userPrompt user input
     * @param onComplete callback to execute when agent finishes (for saving memory, etc.)
     * @return execution result in streaming form
     */
    public SseEmitter runStream(String userPrompt, Runnable onComplete) {
        SseEmitter emitter = new SseEmitter(330000L);
        final String capturedUserId = this.userId;
        final ObjectMapper objectMapper = new ObjectMapper();
        
        CompletableFuture.runAsync(() ->
                {
                    try {
                        UserContext.setUserId(capturedUserId);
                        //check exceptions - 允许从 IDLE 或 WAITING_FOR_INPUT 状态启动
                        if (this.state != AgentState.IDLE && this.state != AgentState.WAITING_FOR_INPUT) {
                            StepResultDTO errorResult = StepResultDTO.builder()
                                    .stepType("error")
                                    .error("CAN'T RUN PROXY IN STATE " + this.state)
                                    .build();
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(objectMapper.writeValueAsString(errorResult), MediaType.APPLICATION_JSON));
                            emitter.complete();
                            return;
                        }
                        if (StrUtil.isEmpty(userPrompt)) {
                            StepResultDTO errorResult = StepResultDTO.builder()
                                    .stepType("error")
                                    .error("EMPTY PROMPT")
                                    .build();
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(objectMapper.writeValueAsString(errorResult), MediaType.APPLICATION_JSON));
                            emitter.complete();
                            return;
                        }
                        // change state
                        this.state = AgentState.RUNNING;
                        //save context and result
                        messageList.add(new UserMessage(userPrompt));
                    } catch (Exception e){
                        emitter.completeWithError(e);
                        return;
                    }
                    //  execute - 在 FINISHED 或 WAITING_FOR_INPUT 时停止循环
                    try {
                        for (int i = 0; i < maxStep && this.state == AgentState.RUNNING; i++) {
                            currentStep = i + 1;
                            log.info("Step: {}/{}", currentStep, maxStep);
                            
                            // 使用结构化的步骤结果
                            StepResultDTO stepResult;
                            if (this instanceof ReActAgent) {
                                ReActAgent reactAgent = (ReActAgent) this;
                                // 使用回调方法，支持发送 pending 状态
                                stepResult = reactAgent.stepWithCallback(currentStep, (pendingEvent) -> {
                                    try {
                                        emitter.send(SseEmitter.event()
                                                .id(currentStep + "-pending")
                                                .name("step")
                                                .data(objectMapper.writeValueAsString(pendingEvent), MediaType.APPLICATION_JSON));
                                    } catch (Exception e) {
                                        log.error("Failed to send pending event: {}", e.getMessage());
                                    }
                                });
                            } else {
                                // 兼容非 ReActAgent 的情况
                                String result = step();
                                stepResult = StepResultDTO.builder()
                                        .stepType("thinking")
                                        .stepNumber(currentStep)
                                        .thinking(result)
                                        .build();
                            }
                            
                            // 发送最终结果
                            emitter.send(SseEmitter.event()
                                    .id(String.valueOf(currentStep))
                                    .name("step")
                                    .data(objectMapper.writeValueAsString(stepResult), MediaType.APPLICATION_JSON));
                        }

                        //CHECK STATUS
                        if (this.state == AgentState.WAITING_FOR_INPUT) {
                            // askHuman 被调用，发送等待用户输入的事件
                            StepResultDTO waitingResult = StepResultDTO.builder()
                                    .stepType("waiting_for_input")
                                    .stepNumber(currentStep)
                                    .build();
                            emitter.send(SseEmitter.event()
                                    .name("waiting")
                                    .data(objectMapper.writeValueAsString(waitingResult), MediaType.APPLICATION_JSON));
                            log.info("Agent waiting for user input");
                        } else if (currentStep >= maxStep) {
                            this.state = AgentState.FINISHED;
                            StepResultDTO completeResult = StepResultDTO.builder()
                                    .stepType("complete")
                                    .stepNumber(currentStep)
                                    .finalAnswer("Terminated: Reached maximum step: " + maxStep)
                                    .build();
                            emitter.send(SseEmitter.event()
                                    .name("complete")
                                    .data(objectMapper.writeValueAsString(completeResult), MediaType.APPLICATION_JSON));
                            log.info("Agent Finished");
                        }

                        // 在完成前执行回调（保存内存等）
                        if (onComplete != null) {
                            try {
                                onComplete.run();
                            } catch (Exception e) {
                                log.error("Error executing onComplete callback: {}", e.getMessage());
                            }
                        }

                        // successfully terminated
                        emitter.complete();
                    } catch (Exception e) {
                        this.state = AgentState.ERROR;
                        log.error("Error: agent can't execute, {}", e.getMessage());
                        emitter.completeWithError(e);
                    } finally {
                        UserContext.clear();
                        clean();
                    }
                });
        emitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            clean();
            log.warn("SSE connection timeout");
        });
        emitter.onCompletion(() -> {
            if (this.state != AgentState.FINISHED) {
                this.state = AgentState.FINISHED;
            }
            clean();
            log.info("Agent [{}] terminated, SSE finished", this.name);
        });
        return emitter;
    }
}
