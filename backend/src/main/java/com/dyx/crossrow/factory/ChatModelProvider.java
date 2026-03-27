package com.dyx.crossrow.factory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pure model registry — holds references to all available ChatModel beans
 * and provides lookup by name. No user/business logic.
 */
@Slf4j
@Component
public class ChatModelProvider {

    private final ChatModel geminiModel;
    private final ChatModel qwenModel;
    private final Map<String, ChatModel> models = new HashMap<>();

    public static final String MODEL_GEMINI = "gemini";
    public static final String MODEL_QWEN = "qwen";
    public static final String DEFAULT_MODEL = MODEL_GEMINI;

    public ChatModelProvider(
            @Qualifier("vertexAiGeminiChat") ChatModel geminiModel,
            @Autowired(required = false) @Qualifier("openAiChatModel") ChatModel qwenModel) {
        this.geminiModel = geminiModel;
        this.qwenModel = qwenModel;

        models.put(MODEL_GEMINI, geminiModel);
        if (qwenModel != null) {
            models.put(MODEL_QWEN, qwenModel);
            log.info("ChatModelProvider initialized with models: gemini, qwen");
        } else {
            log.warn("Qwen model not available, only gemini is configured");
        }
    }

    public ChatModel getByName(String modelName) {
        ChatModel model = models.get(modelName);
        if (model == null) {
            log.warn("Model '{}' not found, using default", modelName);
            return geminiModel;
        }
        return model;
    }

    public ChatModel getGeminiModel() {
        return geminiModel;
    }

    public ChatModel getQwenModel() {
        if (qwenModel == null) {
            log.warn("Qwen model not available, returning Gemini instead");
            return geminiModel;
        }
        return qwenModel;
    }

    public Set<String> getAvailableModels() {
        return models.keySet();
    }

    public boolean isModelAvailable(String modelName) {
        return models.containsKey(modelName);
    }

    public boolean isQwenAvailable() {
        return qwenModel != null;
    }
}
