package com.dyx.crossrow.agent;

import cn.hutool.core.util.StrUtil;
import com.dyx.crossrow.model.AgentState;
import com.dyx.crossrow.model.ReviewResult;
import com.dyx.crossrow.model.dto.MediaContentDTO;
import com.dyx.crossrow.model.dto.StepResultDTO;
import com.dyx.crossrow.advisor.PromptInjectionGuardAdvisor;
import com.dyx.crossrow.exceptions.AgentStateException;
import com.dyx.crossrow.exceptions.EmptyUserPromptException;
import com.dyx.crossrow.exceptions.PromptInjectionDetectedException;
import com.dyx.crossrow.utils.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.http.MediaType;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
    //Review
    @Autowired(required = false)  // 可选注入
    private ReviewAgent reviewAgent;
    @Value("${agent.review.enabled:false}")
    private boolean reviewEnabled;
    @Value("${agent.review.max-retries:2}")
    private int maxReviewRetries;

    /**
     * @param userPrompt user input
     * @return execution result
     */
    public String run(String userPrompt) {
        return run(userPrompt, null);
    }

    /**
     * @param userPrompt user input
     * @param images image list (GCS URLs)
     * @return execution result
     */
    public String run(String userPrompt, List<MediaContentDTO> images) {
        if (this.state != AgentState.IDLE && this.state != AgentState.WAITING_FOR_INPUT) {
            throw new AgentStateException(this.state);
        }
        if (StrUtil.isEmpty(userPrompt)) {
            throw new EmptyUserPromptException();
        }
        if (PromptInjectionGuardAdvisor.detectInjection(userPrompt)) {
            throw new PromptInjectionDetectedException(
                    userPrompt.length() <= 200 ? userPrompt : userPrompt.substring(0, 200) + "...");
        }
        this.state = AgentState.RUNNING;
        //save context and result
        messageList.add(buildUserMessage(userPrompt, images));

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
     * 从 StepResultDTO 中提取最终输出内容
     */
    private String extractFinalOutput(StepResultDTO stepResult) {
        if (stepResult == null) {
            return "";
        }
        if (stepResult.getFinalAnswer() != null && !stepResult.getFinalAnswer().isEmpty()) {
            return stepResult.getFinalAnswer();
        }
        if (stepResult.getThinking() != null && !stepResult.getThinking().isEmpty()) {
            return stepResult.getThinking();
        }
        return "";
    }

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
        return runStream(userPrompt, null, onComplete, null);
    }

    /**
     * @param userPrompt user input
     * @param onComplete callback to execute when agent finishes (for saving memory, etc.)
     * @param onBeforeComplete callback to execute before emitter.complete() (for sending title updates, etc.)
     * @return execution result in streaming form
     */
    public SseEmitter runStream(String userPrompt, Runnable onComplete, Consumer<SseEmitter> onBeforeComplete) {
        return runStream(userPrompt, null, onComplete, onBeforeComplete);
    }

    /**
     * @param userPrompt user input
     * @param images image list (GCS URLs)
     * @param onComplete callback to execute when agent finishes (for saving memory, etc.)
     * @param onBeforeComplete callback to execute before emitter.complete() (for sending title updates, etc.)
     *                         This is called synchronously in the async thread, ensuring events are sent before stream closes.
     * @return execution result in streaming form
     */
    public SseEmitter runStream(String userPrompt, List<MediaContentDTO> images, Runnable onComplete, Consumer<SseEmitter> onBeforeComplete) {
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
                        if (PromptInjectionGuardAdvisor.detectInjection(userPrompt)) {
                            StepResultDTO injectionResult = StepResultDTO.builder()
                                    .stepType("injection_blocked")
                                    .reason("Potential prompt injection attack detected. Your input has been blocked for security reasons.")
                                    .build();
                            emitter.send(SseEmitter.event()
                                    .name("step")
                                    .data(objectMapper.writeValueAsString(injectionResult), MediaType.APPLICATION_JSON));
                            emitter.complete();
                            return;
                        }
                        this.state = AgentState.RUNNING;
                        //save context and result
                        messageList.add(buildUserMessage(userPrompt, images));
                    } catch (Exception e){
                        emitter.completeWithError(e);
                        return;
                    }
                    //  execute - 在 FINISHED 或 WAITING_FOR_INPUT 时停止循环
                    try {
                        StepResultDTO lastStepResult = null;
                        
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
                            
                            lastStepResult = stepResult;
                            
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
                        } else if (this.state == AgentState.FINISHED) {
                            // Agent 正常完成，进行 Review（如果启用）
                            if (reviewEnabled && reviewAgent != null) {
                                String finalOutput = extractFinalOutput(lastStepResult);
                                
                                for (int retry = 0; retry < maxReviewRetries; retry++) {
                                    // 发送 review 开始事件
                                    emitter.send(SseEmitter.event()
                                            .name("review")
                                            .data(objectMapper.writeValueAsString(
                                                    StepResultDTO.builder()
                                                            .stepType("review_pending")
                                                            .stepNumber(retry + 1)
                                                            .build()
                                            ), MediaType.APPLICATION_JSON));
                                    
                                    ReviewResult review = reviewAgent.review(userPrompt, finalOutput);
                                    
                                    // 发送 review 结果事件
                                    emitter.send(SseEmitter.event()
                                            .name("review")
                                            .data(objectMapper.writeValueAsString(
                                                    StepResultDTO.builder()
                                                            .stepType("review_result")
                                                            .stepNumber(retry + 1)
                                                            .reviewApproved(review.isApproved())
                                                            .reviewReason(review.getReason())
                                                            .build()
                                            ), MediaType.APPLICATION_JSON));
                                    
                                    if (review.isApproved()) {
                                        log.info("Review approved: {}", review.getReason());
                                        break;
                                    }
                                    
                                    log.info("Review rejected (attempt {}): {}", retry + 1, review.getReason());
                                    
                                    // 最后一次重试失败，不再继续
                                    if (retry >= maxReviewRetries - 1) {
                                        break;
                                    }
                                    
                                    // 拒绝：注入反馈，继续执行
                                    String feedback = "Review feedback: " + review.getReason() + ". Please address this issue.";
                                    messageList.add(new UserMessage(feedback));
                                    
                                    // 继续执行几步（每次重试最多执行 maxReviewRetries 步）
                                    this.state = AgentState.RUNNING;
                                    for (int j = 0; j < maxReviewRetries && this.state == AgentState.RUNNING; j++) {
                                        currentStep++;
                                        log.info("Retry Step: {}/{}", currentStep, maxStep);
                                        
                                        StepResultDTO retryStepResult;
                                        if (this instanceof ReActAgent) {
                                            ReActAgent reactAgent = (ReActAgent) this;
                                            retryStepResult = reactAgent.stepWithCallback(currentStep, (pendingEvent) -> {
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
                                            String result = step();
                                            retryStepResult = StepResultDTO.builder()
                                                    .stepType("thinking")
                                                    .stepNumber(currentStep)
                                                    .thinking(result)
                                                    .build();
                                        }
                                        
                                        lastStepResult = retryStepResult;
                                        finalOutput = extractFinalOutput(retryStepResult);
                                        
                                        emitter.send(SseEmitter.event()
                                                .id(String.valueOf(currentStep))
                                                .name("step")
                                                .data(objectMapper.writeValueAsString(retryStepResult), MediaType.APPLICATION_JSON));
                                    }
                                }
                            }
                            
                            // 发送完成事件
                            StepResultDTO completeResult = StepResultDTO.builder()
                                    .stepType("complete")
                                    .stepNumber(currentStep)
                                    .finalAnswer(extractFinalOutput(lastStepResult))
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

                        // 在 emitter.complete() 之前发送额外事件（如标题更新）
                        // 这确保事件在流关闭前发送
                        if (onBeforeComplete != null) {
                            try {
                                log.info("[Agent] 执行 onBeforeComplete 回调（标题生成）...");
                                onBeforeComplete.accept(emitter);
                                log.info("[Agent] onBeforeComplete 回调执行完成");
                            } catch (Exception e) {
                                log.error("Error executing onBeforeComplete callback: {}", e.getMessage());
                            }
                        } else {
                            log.info("[Agent] onBeforeComplete 回调为 null，跳过");
                        }

                        // successfully terminated
                        log.info("[Agent] 准备调用 emitter.complete()");
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

    /**
     * Build UserMessage with optional images
     */
    protected UserMessage buildUserMessage(String text, List<MediaContentDTO> images) {
        if (images == null || images.isEmpty()) {
            return new UserMessage(text);
        }

        List<Media> mediaList = images.stream()
                .filter(dto -> dto.getUrl() != null && !dto.getUrl().isEmpty())
                .map(dto -> new Media(
                        MimeTypeUtils.parseMimeType(dto.getMimeType()),
                        URI.create(dto.getUrl())))
                .toList();

        if (mediaList.isEmpty()) {
            return new UserMessage(text);
        }

        log.info("Building UserMessage with {} image(s)", mediaList.size());
        return UserMessage.builder()
                .text(text)
                .media(mediaList)
                .build();
    }
}
