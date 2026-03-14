package com.dyx.crossrow.service;

import com.dyx.crossrow.properties.ModelRouterProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Intelligent model routing service
 * Uses lightweight model (Qwen) to evaluate task complexity and route to appropriate model:
 * - Simple tasks: Qwen (cost-effective)
 * - Complex tasks (image/code/reasoning): Gemini (more capable)
 */
@Slf4j
@Service
public class ModelRouterService {

    private final ModelRouterProperties properties;
    private final Map<String, ChatModel> models = new HashMap<>();
    private final ChatModel defaultModel;
    private final ChatClient reviewClient;

    private static final String REVIEW_PROMPT = """
            You are a task complexity evaluator. Analyze the user's input and determine whether it requires a powerful model or a lightweight model.
            
            ## COMPLEX tasks (require powerful model):
            - Image generation, analysis, or processing
            - Code generation or analysis (beyond simple snippets)
            - Complex logical reasoning or mathematical calculations
            - Multi-step planning or long-form content creation
            - Professional domain analysis (medical, legal, financial, etc.)
            - Multimodal understanding (mixed text and images)
            - Ambiguous tasks requiring significant inference
            
            ## SIMPLE tasks (can use lightweight model):
            - Simple Q&A, casual conversation
            - Text translation or summarization
            - Basic information lookup
            - Format conversion
            - Simple copywriting
            
            Return ONLY a JSON response.
            """;

    /**
     * Constructor with dependency injection
     * 
     * Both models are auto-configured by Spring AI starters via application.yml:
     * - Gemini: "vertexAiGeminiChat" from spring-ai-starter-model-vertex-ai-gemini
     * - Qwen: "openAiChatModel" from spring-ai-starter-model-openai (via DashScope OpenAI-compatible API)
     */
    public ModelRouterService(
            ModelRouterProperties properties,
            @Qualifier("vertexAiGeminiChat") ChatModel geminiChatModel,
            @Autowired(required = false) @Qualifier("openAiChatModel") ChatModel qwenChatModel
    ) {
        this.properties = properties;
        this.defaultModel = geminiChatModel;

        models.put("gemini", geminiChatModel);
        
        if (qwenChatModel != null) {
            models.put("qwen", qwenChatModel);
            log.info("Model router initialized: gemini (vertexAiGeminiChat), qwen (openAiChatModel via DashScope)");
            
            this.reviewClient = ChatClient.builder(qwenChatModel)
                    .defaultSystem(REVIEW_PROMPT)
                    .build();
        } else {
            log.warn("Qwen/OpenAI model not configured, using Gemini for review");
            this.reviewClient = ChatClient.builder(geminiChatModel)
                    .defaultSystem(REVIEW_PROMPT)
                    .build();
        }
    }

    /**
     * 智能路由：根据 AI 评审结果选择模型
     */
    public ChatModel route(String input) {
        if (!properties.isEnabled()) {
            log.debug("模型路由已禁用，使用默认模型");
            return defaultModel;
        }

        TaskReview review = reviewTask(input);
        
        String selectedModelName = review.isComplex() 
                ? properties.getComplexModel() 
                : properties.getSimpleModel();
        
        ChatModel selectedModel = models.get(selectedModelName);
        if (selectedModel == null) {
            log.warn("模型 {} 不可用，回退到默认模型", selectedModelName);
            return defaultModel;
        }
        
        log.info("AI评审路由: 复杂度={}, 原因={}, 选择模型={}", 
                review.isComplex() ? "复杂" : "简单", 
                review.reason(), 
                selectedModelName);
        
        return selectedModel;
    }

    /**
     * Evaluate task complexity using AI
     */
    public TaskReview reviewTask(String input) {
        if (input == null || input.isBlank()) {
            return new TaskReview(false, "empty input", "simple");
        }

        try {
            TaskReview review = reviewClient
                    .prompt()
                    .user("Evaluate the following user input:\n\n" + input)
                    .call()
                    .entity(TaskReview.class);

            if (review == null) {
                log.warn("AI review returned null, falling back to complex model");
                return new TaskReview(true, "review returned null", "unknown");
            }

            log.debug("Task review completed: {}", review);
            return review;

        } catch (Exception e) {
            log.warn("AI review failed, falling back to complex model: {}", e.getMessage());
            return new TaskReview(true, "review failed: " + e.getMessage(), "unknown");
        }
    }

    /**
     * 根据模型名称获取指定模型
     */
    public ChatModel getByName(String modelName) {
        ChatModel model = models.get(modelName);
        if (model == null) {
            log.warn("模型 {} 不存在，使用默认模型", modelName);
            return defaultModel;
        }
        return model;
    }

    /**
     * 获取路由决策信息（用于调试/日志）
     */
    public RouteDecision getRouteDecision(String input) {
        TaskReview review = reviewTask(input);
        String selectedModel = review.isComplex() 
                ? properties.getComplexModel() 
                : properties.getSimpleModel();
        
        return new RouteDecision(review, selectedModel);
    }

    /**
     * 检查 Qwen 模型是否可用
     */
    public boolean isQwenAvailable() {
        return models.containsKey("qwen");
    }

    /**
     * 获取所有可用模型名称
     */
    public Set<String> getAvailableModels() {
        return models.keySet();
    }

    /**
     * 任务评审结果
     */
    public record TaskReview(
            @JsonProperty("is_complex")
            @JsonPropertyDescription("是否为复杂任务")
            boolean isComplex,
            
            @JsonProperty("reason")
            @JsonPropertyDescription("判断原因")
            String reason,
            
            @JsonProperty("category")
            @JsonPropertyDescription("任务类别: simple/complex/image/code/reasoning")
            String category
    ) {}

    /**
     * 路由决策记录
     */
    public record RouteDecision(
            TaskReview review,
            String selectedModel
    ) {}
}
