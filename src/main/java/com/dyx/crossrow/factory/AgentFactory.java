package com.dyx.crossrow.factory;

import com.dyx.crossrow.agent.CrossRowAgent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AgentFactory {
    // 利用 ObjectProvider 获取多例 (Prototype) 的 CrossRowAgent
    private final ObjectProvider<CrossRowAgent> crossRowAgentProvider;

    public AgentFactory(ObjectProvider<CrossRowAgent> crossRowAgentProvider) {
        this.crossRowAgentProvider = crossRowAgentProvider;
    }

    /**
     * 生产一个带有用户上下文的专属 Agent
     */
    public CrossRowAgent createAgent(String userId, String sessionId) {
        CrossRowAgent agent = crossRowAgentProvider.getObject();
        // 赋予身份标识
        agent.setUserId(userId);
        agent.setSessionId(sessionId);

        return agent;
    }
}
