package com.dyx.crossrow.exceptions;

import com.dyx.crossrow.agent.model.AgentState;

public class AgentStateException extends RuntimeException{

    private final AgentState state;
    public AgentStateException(AgentState state) {
        super("当前状态无法运行: " + state);
        this.state = state;
    }
    public AgentState getAgentState() {
        return state;
    }

}
