package com.dyx.crossrow.factory;

import com.dyx.crossrow.advisor.SimpleAuthAdvisor;
import com.dyx.crossrow.agent.CrossRowAgent;
import com.dyx.crossrow.agent.ExpertAgent;
import com.dyx.crossrow.tool.SimpleToolCallManager;
import com.dyx.crossrow.tool.ToolCallStrategy;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class AgentFactory {
    private final ObjectProvider<CrossRowAgent> crossRowAgentProvider;
    private final SimpleToolCallManager toolCallingManager;
    private final ToolCallStrategy toolCallStrategy;
    private final ChatModel chatModel;
    private final SimpleAuthAdvisor simpleAuthAdvisor;

    // Domain-specific tools
    private final ToolCallback[] philosophyTools;
    private final ToolCallback[] psychologyTools;
    private final ToolCallback[] sociologyTools;

    // Domain-specific prompts
    private final Resource philosophyPrompt;
    private final Resource psychologyPrompt;
    private final Resource sociologyPrompt;
    private final Resource nextStepPrompt;

    public AgentFactory(ObjectProvider<CrossRowAgent> crossRowAgentProvider,
                        SimpleToolCallManager toolCallingManager,
                        ToolCallStrategy toolCallStrategy,
                        ChatModel chatModel,
                        SimpleAuthAdvisor simpleAuthAdvisor,
                        @Qualifier("philosophyTools") ToolCallback[] philosophyTools,
                        @Qualifier("psychologyTools") ToolCallback[] psychologyTools,
                        @Qualifier("sociologyTools") ToolCallback[] sociologyTools,
                        @Value("classpath:/prompts/philosophy-agent-prompt.st") Resource philosophyPrompt,
                        @Value("classpath:/prompts/psychology-agent-prompt.st") Resource psychologyPrompt,
                        @Value("classpath:/prompts/sociology-agent-prompt.st") Resource sociologyPrompt,
                        @Value("classpath:/prompts/next-step-prompt.st") Resource nextStepPrompt) {
        this.crossRowAgentProvider = crossRowAgentProvider;
        this.toolCallingManager = toolCallingManager;
        this.toolCallStrategy = toolCallStrategy;
        this.chatModel = chatModel;
        this.simpleAuthAdvisor = simpleAuthAdvisor;
        this.philosophyTools = philosophyTools;
        this.psychologyTools = psychologyTools;
        this.sociologyTools = sociologyTools;
        this.philosophyPrompt = philosophyPrompt;
        this.psychologyPrompt = psychologyPrompt;
        this.sociologyPrompt = sociologyPrompt;
        this.nextStepPrompt = nextStepPrompt;
    }

    /**
     * Create original CrossRowAgent (for backward compatibility)
     */
    public CrossRowAgent createAgent(String userId, String sessionId) {
        CrossRowAgent agent = crossRowAgentProvider.getObject();
        agent.setUserId(userId);
        agent.setSessionId(sessionId);
        return agent;
    }

    /**
     * Create domain-specific Expert Agent
     * @param domain "philosophy", "psychology", or "sociology"
     */
    public ExpertAgent createExpertAgent(String domain, String userId, String sessionId) {
        ToolCallback[] tools;
        Resource prompt;

        switch (domain.toLowerCase()) {
            case "philosophy":
                tools = philosophyTools;
                prompt = philosophyPrompt;
                break;
            case "psychology":
                tools = psychologyTools;
                prompt = psychologyPrompt;
                break;
            case "sociology":
                tools = sociologyTools;
                prompt = sociologyPrompt;
                break;
            default:
                throw new IllegalArgumentException("Unknown domain: " + domain);
        }

        ExpertAgent agent = new ExpertAgent(
                domain,
                tools,
                toolCallingManager,
                toolCallStrategy,
                chatModel,
                simpleAuthAdvisor,
                prompt,
                nextStepPrompt
        );

        agent.setUserId(userId);
        agent.setSessionId(sessionId);
        return agent;
    }
}
