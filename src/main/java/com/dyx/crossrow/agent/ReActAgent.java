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

    /**
     * 获取最近一次思考的内容（子类可重写）
     * @return 思考内容，如果没有则返回 null
     */
    protected String getThinkingResult() {
        return null;
    }

    @Override
    public String step() {
        try{
        // thinking:
        boolean shouldAct = thinking();
        if (!shouldAct) {
            // 尝试获取 LLM 的实际回答内容
            String thinkingResult = getThinkingResult();
            if (thinkingResult != null && !thinkingResult.isEmpty()) {
                return thinkingResult;
            }
            return "reasoning completed";
        }
        // acting
        return act();
        } catch (Exception e){
            throw new ReActProcessingException();
        }
    }
}
