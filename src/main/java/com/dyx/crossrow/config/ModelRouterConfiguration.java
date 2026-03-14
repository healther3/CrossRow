package com.dyx.crossrow.config;

import com.dyx.crossrow.properties.ModelRouterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Multi-model routing configuration
 * 
 * Both models are auto-configured by Spring AI starters via application.yml:
 * - Gemini: spring-ai-starter-model-vertex-ai-gemini → vertexAiGeminiChat bean
 * - Qwen:   spring-ai-starter-model-openai (DashScope compatible) → openAiChatModel bean
 * 
 * Gemini is marked as @Primary to resolve the "multiple beans found" issue
 * when Spring AI's ChatClientAutoConfiguration tries to auto-wire a single ChatModel.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ModelRouterProperties.class)
public class ModelRouterConfiguration {

    /**
     * Mark Gemini as the primary ChatModel bean.
     * This resolves the conflict when both Gemini and OpenAI (Qwen) models are configured.
     */
    @Bean
    @Primary
    public ChatModel primaryChatModel(@Qualifier("vertexAiGeminiChat") VertexAiGeminiChatModel geminiChatModel) {
        log.info("Setting Gemini as primary ChatModel for auto-configuration");
        return geminiChatModel;
    }
}
