package com.dyx.crossrow.orchestrator;

import com.dyx.crossrow.advisor.PromptInjectionGuardAdvisor;
import com.dyx.crossrow.agent.ExpertAgent;
import com.dyx.crossrow.factory.AgentFactory;
import com.dyx.crossrow.service.RetryableLlmCaller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Orchestrator for routing user queries to appropriate expert agents.
 * Always uses Gemini model for routing decisions (better reasoning capabilities).
 */
@Slf4j
@Component
public class ExpertOrchestrator {

    private final ChatClient routerClient;
    private final AgentFactory agentFactory;
    private final ObjectMapper objectMapper;
    private final RetryableLlmCaller retryableLlmCaller;

    private static final List<String> VALID_DOMAINS = List.of("philosophy", "psychology", "sociology");
    private static final String DEFAULT_DOMAIN = "philosophy";

    public ExpertOrchestrator(@Qualifier("vertexAiGeminiChat") ChatModel chatModel,
                              AgentFactory agentFactory,
                              RetryableLlmCaller retryableLlmCaller,
                              @Value("classpath:/prompts/orchestrator-prompt.st") Resource orchestratorPromptResource) {
        this.agentFactory = agentFactory;
        this.objectMapper = new ObjectMapper();
        this.retryableLlmCaller = retryableLlmCaller;

        SystemPromptTemplate promptTemplate = new SystemPromptTemplate(orchestratorPromptResource);

        this.routerClient = ChatClient.builder(chatModel)
                .defaultSystem(promptTemplate.render())
                .defaultAdvisors(new PromptInjectionGuardAdvisor())
                .build();
    }

    /**
     * Route user message to appropriate expert agent and execute
     */
    public SseEmitter route(String userMessage, String userId, String sessionId) {
        // 1. Determine which expert to use
        String domain = determineExpert(userMessage);
        log.info("Routing to {} expert for user: {}", domain, userId);

        // 2. Create the expert agent
        ExpertAgent expert = agentFactory.createExpertAgent(domain, userId, sessionId);

        // 3. Execute and return SSE stream
        return expert.runStream(userMessage, () -> {
            log.info("{} expert completed for session: {}", domain, sessionId);
        });
    }

    /**
     * Route and execute with memory callback
     */
    public SseEmitter route(String userMessage, String userId, String sessionId, Runnable onComplete) {
        String domain = determineExpert(userMessage);
        log.info("Routing to {} expert for user: {}", domain, userId);

        ExpertAgent expert = agentFactory.createExpertAgent(domain, userId, sessionId);

        return expert.runStream(userMessage, onComplete);
    }

    /**
     * Call LLM to determine which expert should handle the query
     */
    private String determineExpert(String userMessage) {
        try {
            String response = retryableLlmCaller.callWithRetry(() ->
                    routerClient.prompt()
                            .user(userMessage)
                            .call()
                            .content()
            );

            log.debug("Router raw response: {}", response);

            // Clean markdown code blocks if present
            String cleanedResponse = cleanJsonResponse(response);
            log.debug("Router cleaned response: {}", cleanedResponse);

            // Parse JSON response
            JsonNode json = objectMapper.readTree(cleanedResponse);
            String expert = json.get("expert").asText().toLowerCase();
            String reason = json.has("reason") ? json.get("reason").asText() : "N/A";

            log.info("Router decision: {} (reason: {})", expert, reason);

            // Validate domain
            if (VALID_DOMAINS.contains(expert)) {
                return expert;
            } else {
                log.warn("Invalid domain '{}' returned, falling back to {}", expert, DEFAULT_DOMAIN);
                return DEFAULT_DOMAIN;
            }

        } catch (Exception e) {
            log.error("Router failed to parse response, falling back to {}: {}", DEFAULT_DOMAIN, e.getMessage());
            return DEFAULT_DOMAIN;
        }
    }

    /**
     * Remove markdown code block wrappers from LLM response
     */
    private String cleanJsonResponse(String response) {
        if (response == null) {
            return "{}";
        }
        String cleaned = response.trim();
        
        // Remove ```json or ``` at the beginning
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        
        // Remove ``` at the end
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        return cleaned.trim();
    }

    /**
     * Get expert domain without executing (for testing/preview)
     */
    public String previewRoute(String userMessage) {
        return determineExpert(userMessage);
    }
}
