package com.dyx.crossrow.agent;

import cn.hutool.core.util.StrUtil;
import com.dyx.crossrow.agent.model.AgentState;
import com.dyx.crossrow.exceptions.AgentStateException;
import com.dyx.crossrow.exceptions.EmptyUserPromptException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

@Data
@Slf4j
public abstract class BaseAgent {
    // attributes
    private String name;
    private String systemPrompt;
    private String nextStepPrompt;
    //agent state
    private AgentState state = AgentState.IDLE;
    //restricted max step
    private int currentStep = 0;
    private int maxStep = 10;
    //LLM
    private ChatClient chatClient;
    //memory
    private List<Message> messageList;

    /**
     *
     * @param userPrompt user input
     * @return execution result
     */
    public String run(String userPrompt ) {
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

        } catch (Exception e){
            this.state = AgentState.ERROR;
        log.error("Error: agent can't execute, {}", e.getMessage());
        return "Error: agent can't execute, " + e.getMessage();
        } finally {
            clean();
        }
    }

    /**
     * Single step
     * @return result
     */
    public abstract String step();

    /**
     * clean resources
     */
    protected void clean(){
        log.debug("Cleaning agent [{}] resources, previous state: {}", this.name, this.state);

        this.state = AgentState.IDLE;
        this.currentStep = 0;

        if (this.messageList != null) {
            this.messageList.clear();
        }

        log.debug("Agent [{}] cleaned successfully", this.name);
    }
}
