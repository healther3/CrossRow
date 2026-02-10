package com.dyx.crossrow.agent;

import com.dyx.crossrow.exceptions.ReActProcessingException;

/**
 * loop: Reasoning and Acting
 */
public abstract class ReActAgent extends BaseAgent{
    /**
     * execute current state and decide next step
     * @return true: act ; false: reasoning
      */
    public abstract boolean thinking();

    /**
     * execute the action;including tool calling
     * @return the results of execution
     */
    public abstract String act();

    @Override
    public String step() {
        try{
        // thinking:
        boolean shouldAct = thinking();
        if (!shouldAct) {
            return "finish reasoning";
        }
        // acting
        return act();
        } catch (Exception e){
            throw new ReActProcessingException();
        }
    }
}
