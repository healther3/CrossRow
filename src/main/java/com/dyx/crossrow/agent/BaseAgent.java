package com.dyx.crossrow.agent;

import cn.hutool.core.util.StrUtil;
import com.dyx.crossrow.model.AgentState;
import com.dyx.crossrow.exceptions.AgentStateException;
import com.dyx.crossrow.exceptions.EmptyUserPromptException;
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
        //check exceptions
        if (this.state != AgentState.IDLE) {
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
            //execute
            for (int i = 0; i < maxStep && this.state != AgentState.FINISHED; i++) {
                currentStep = i + 1;
                log.info("Step: {}/{}", currentStep, maxStep);
                String stepResult = step();
                results.add("Step: " + currentStep + ":" + stepResult);
            }

            //CHECK STATUS
            if (currentStep >= maxStep) {
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
     */
    protected void clean() {
        log.debug("Cleaning agent [{}] resources, previous state: {}", this.name, this.state);

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
        CompletableFuture.runAsync(() ->
                {
                    try {
                        //check exceptions
                        if (this.state != AgentState.IDLE) {
                            emitter.send(SseEmitter.event()
                                    .data("ERROR: CAN'T RUN PROXY IN STATE " + this.state, MediaType.TEXT_PLAIN));
                            emitter.complete();
                            return;
                        }
                        if (StrUtil.isEmpty(userPrompt)) {
                            emitter.send(SseEmitter.event()
                                    .data("ERROR: EMPTY PROMPT", MediaType.TEXT_PLAIN));
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
                    //  execute
                    List<String> results = new ArrayList<>();
                    try {
                        for (int i = 0; i < maxStep && this.state != AgentState.FINISHED; i++) {
                            currentStep = i + 1;
                            log.info("Step: {}/{}", currentStep, maxStep);
                            // get result from single step
                            String stepResult = step();
                            String stepMessage = stepResult;
                            results.add(stepMessage);
                            // 使用标准 SSE 格式发送
                            emitter.send(SseEmitter.event()
                                    .id(String.valueOf(currentStep))
                                    .name("step")
                                    .data(stepMessage, MediaType.TEXT_PLAIN));
                        }

                        //CHECK STATUS
                        if (currentStep >= maxStep) {
                            this.state = AgentState.FINISHED;
                            String finishMessage = "Terminated: Reached maximum step: " + maxStep;
                            results.add(finishMessage);
                            emitter.send(SseEmitter.event()
                                    .name("complete")
                                    .data(finishMessage, MediaType.TEXT_PLAIN));
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
