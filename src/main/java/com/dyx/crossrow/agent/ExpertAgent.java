package com.dyx.crossrow.agent;

import com.dyx.crossrow.advisor.MyLogAdvisor;
import com.dyx.crossrow.advisor.SimpleAuthAdvisor;
import com.dyx.crossrow.advisor.SimpleQuotaAdvisor;
import com.dyx.crossrow.model.ToolChoice;
import com.dyx.crossrow.tool.SimpleToolCallManager;
import com.dyx.crossrow.tool.ToolCallStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * Expert Agent for multi-agent architecture.
 * Each expert has its own domain (philosophy/psychology/sociology),
 * specific tools, and customized prompt.
 */
public class ExpertAgent extends ToolCallAgent {

    private final String domain;

    public ExpertAgent(String domain,
                       ToolCallback[] domainTools,
                       SimpleToolCallManager toolCallingManager,
                       ToolCallStrategy toolCallStrategy,
                       ChatModel chatModel,
                       SimpleAuthAdvisor simpleAuthAdvisor,
                       Resource systemPromptResource,
                       Resource nextStepPromptResource) {
        super(domainTools,
              List.of("askHuman"),
              toolCallingManager,
              ToolChoice.AUTO,
              toolCallStrategy);

        this.domain = domain;

        // Set agent name based on domain
        setName(capitalize(domain) + "Expert");

        // Load prompts
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPromptResource);
        PromptTemplate nextStepPromptTemplate = new PromptTemplate(nextStepPromptResource);
        setSystemPrompt(systemPromptTemplate.render());
        setNextStepPrompt(nextStepPromptTemplate.render());
        setMaxStep(5);

        // Initialize chat client
        VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                .googleSearchRetrieval(false)
                .build();

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem(getSystemPrompt())
                .defaultAdvisors(
                        simpleAuthAdvisor,
                        new SimpleQuotaAdvisor(5),
                        new MyLogAdvisor(100)
                )
                .defaultOptions(options)
                .build();
        setChatClient(chatClient);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
