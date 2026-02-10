package com.dyx.crossrow.agent;

import cn.hutool.core.util.StrUtil;
import com.dyx.crossrow.agent.model.AgentState;
import com.dyx.crossrow.exceptions.AgentStateException;
import com.dyx.crossrow.exceptions.EmptyUserPromptException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

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
        if (this.state != AgentState.IDLE){
            throw new AgentStateException(this.state);
        }
        if (StrUtil.isEmpty(userPrompt)) {
            throw new EmptyUserPromptException();
        }
        return "";
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

    }
}
