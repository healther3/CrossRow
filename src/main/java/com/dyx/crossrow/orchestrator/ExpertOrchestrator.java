package com.dyx.crossrow.orchestrator;

import com.dyx.crossrow.agent.ExpertAgent;
import com.dyx.crossrow.factory.AgentFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@Component
public class ExpertOrchestrator {

    private final ChatClient routerClient;
    private final AgentFactory agentFactory;
    private final ObjectMapper objectMapper;

    private static final List<String> VALID_DOMAINS = List.of("philosophy", "psychology", "sociology");
    private static final String DEFAULT_DOMAIN = "philosophy";

    public ExpertOrchestrator(ChatModel chatModel,
                              AgentFactory agentFactory,
                              @Value("classpath:/prompts/orchestrator-prompt.st") Resource orchestratorPrompt) {
        this.agentFactory = agentFactory;
        this.objectMapper = new ObjectMapper();

        // Build router client with orchestrator prompt
        SystemPromptTemplate promptTemplate = new SystemPromptTemplate(orchestratorPrompt);
        String systemPrompt = promptTemplate.render();

        this.routerClient = ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
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
            String response = routerClient.prompt()
                    .user(userMessage)
                    .call()
                    .content();

            log.debug("Router response: {}", response);

            // Parse JSON response
            JsonNode json = objectMapper.readTree(response);
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
     * Get expert domain without executing (for testing/preview)
     */
    public String previewRoute(String userMessage) {
        return determineExpert(userMessage);
    }
}
