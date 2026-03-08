package com.dyx.crossrow.agent;

import com.dyx.crossrow.advisor.MyLogAdvisor;
import com.dyx.crossrow.advisor.SimpleAuthAdvisor;
import com.dyx.crossrow.advisor.SimpleQuotaAdvisor;
import com.dyx.crossrow.model.ToolChoice;
import com.dyx.crossrow.tool.SimpleToolCallManager;
import com.dyx.crossrow.tool.ToolCallStrategy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CrossRowAgent extends ToolCallAgent{

    @jakarta.annotation.Resource(name = "hybridRagAdvisor")
    private final Advisor hybridRagAdvisor;

    @jakarta.annotation.Resource
    private final SimpleAuthAdvisor simpleAuthAdvisor;

    public CrossRowAgent(ToolCallback[] allTools,
                         List<String> specialToolNames,
                         SimpleToolCallManager toolCallingManager,
                         ToolCallStrategy toolCallStrategy,
                         ChatModel chatModel,
                         SimpleAuthAdvisor simpleAuthAdvisor,
                         @Value("classpath:/prompts/orchestrator-prompt.st") Resource systemPromptResource,
                         @Value("classpath:/prompts/next-step-prompt.st") Resource nextStepPromptResource,
                         @Qualifier("hybridRagAdvisor") Advisor hybridRagAdvisor) {
        super(  allTools,
                List.of("askHuman"),
                toolCallingManager,
                ToolChoice.AUTO,
                toolCallStrategy);
        this.hybridRagAdvisor = hybridRagAdvisor;
        this.simpleAuthAdvisor = simpleAuthAdvisor;

        // set name
        setName("NoName");

        // set default prompt and next-step prompt
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPromptResource);
        PromptTemplate nextStepPromptTemplate = new PromptTemplate(nextStepPromptResource);
        setSystemPrompt(systemPromptTemplate.render());
        setNextStepPrompt(nextStepPromptTemplate.render());
        setMaxStep(15);

        // initialize chat client
        VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                .googleSearchRetrieval(false)
                .build();

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem(getSystemPrompt())
                .defaultAdvisors(
                        simpleAuthAdvisor,
                        new SimpleQuotaAdvisor(5),
                        // customized logger advisor
                        new MyLogAdvisor(100)
                        // customized enhanced advisor
                        // new ReReadingAdvisor()
                        //hybridRagAdvisor
                )
                .defaultOptions(options)
                .build();
        setChatClient(chatClient);
    }



}
