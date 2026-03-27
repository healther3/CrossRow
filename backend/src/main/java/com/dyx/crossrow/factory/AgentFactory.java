package com.dyx.crossrow.factory;

import com.dyx.crossrow.advisor.SimpleAuthAdvisor;
import com.dyx.crossrow.agent.CrossRowAgent;
import com.dyx.crossrow.agent.ExpertAgent;
import com.dyx.crossrow.agent.ReviewAgent;
import com.dyx.crossrow.service.QuotaService;
import com.dyx.crossrow.tool.SimpleToolCallManager;
import com.dyx.crossrow.tool.ToolCallStrategy;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Factory for creating Agent instances.
 * Uses ChatModelProvider to get Gemini model for all agents (better reasoning capabilities).
 */
@Component
public class AgentFactory {
    private final ObjectProvider<CrossRowAgent> crossRowAgentProvider;
    private final SimpleToolCallManager toolCallingManager;
    private final ToolCallStrategy toolCallStrategy;
    private final SimpleAuthAdvisor simpleAuthAdvisor;
    private final ChatModelProvider chatModelProvider;
    private final QuotaService quotaService;
    
    @Autowired(required = false)
    private ReviewAgent reviewAgent;

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
                        SimpleAuthAdvisor simpleAuthAdvisor,
                        ChatModelProvider chatModelProvider,
                        QuotaService quotaService,
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
        this.simpleAuthAdvisor = simpleAuthAdvisor;
        this.chatModelProvider = chatModelProvider;
        this.quotaService = quotaService;
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
                chatModelProvider.getGeminiModel(),
                simpleAuthAdvisor,
                quotaService,
                prompt,
                nextStepPrompt
        );

        agent.setUserId(userId);
        agent.setSessionId(sessionId);
        
        // 手动注入 ReviewAgent（因为 ExpertAgent 不是 Spring Bean）
        if (reviewAgent != null) {
            agent.setReviewAgent(reviewAgent);
        }
        
        return agent;
    }
}
