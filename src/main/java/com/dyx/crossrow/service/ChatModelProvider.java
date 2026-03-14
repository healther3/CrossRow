package com.dyx.crossrow.service;

import com.dyx.crossrow.model.User;
import com.dyx.crossrow.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Unified ChatModel provider service.
 * Manages model selection based on user preferences.
 * Agent-related features always use Gemini for better reasoning capabilities.
 */
@Slf4j
@Service
public class ChatModelProvider {

    private final ChatModel geminiModel;
    private final ChatModel qwenModel;
    private final UserRepository userRepository;
    private final Map<String, ChatModel> models = new HashMap<>();

    public static final String MODEL_GEMINI = "gemini";
    public static final String MODEL_QWEN = "qwen";
    public static final String DEFAULT_MODEL = MODEL_GEMINI;

    public ChatModelProvider(
            @Qualifier("vertexAiGeminiChat") ChatModel geminiModel,
            @Autowired(required = false) @Qualifier("openAiChatModel") ChatModel qwenModel,
            UserRepository userRepository) {
        this.geminiModel = geminiModel;
        this.qwenModel = qwenModel;
        this.userRepository = userRepository;

        models.put(MODEL_GEMINI, geminiModel);
        if (qwenModel != null) {
            models.put(MODEL_QWEN, qwenModel);
            log.info("ChatModelProvider initialized with models: gemini, qwen");
        } else {
            log.warn("Qwen model not available, only gemini is configured");
        }
    }

    /**
     * Get ChatModel based on user's preference setting.
     * Falls back to default model if preference is invalid or user not found.
     */
    public ChatModel getModelForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            log.debug("No userId provided, using default model: {}", DEFAULT_MODEL);
            return geminiModel;
        }

        return userRepository.findById(userId)
                .map(user -> {
                    String preferredModel = user.getPreferredModel();
                    if (preferredModel == null || preferredModel.isBlank()) {
                        preferredModel = DEFAULT_MODEL;
                    }
                    ChatModel model = models.get(preferredModel);
                    if (model == null) {
                        log.warn("User {} preferred model '{}' not available, using default", userId, preferredModel);
                        return geminiModel;
                    }
                    log.debug("Using model '{}' for user {}", preferredModel, userId);
                    return model;
                })
                .orElseGet(() -> {
                    log.debug("User {} not found, using default model", userId);
                    return geminiModel;
                });
    }

    /**
     * Get Gemini model directly (for Agent use).
     * Agents always use Gemini for better reasoning capabilities.
     */
    public ChatModel getGeminiModel() {
        return geminiModel;
    }

    /**
     * Get Qwen model directly (if available).
     */
    public ChatModel getQwenModel() {
        if (qwenModel == null) {
            log.warn("Qwen model not available, returning Gemini instead");
            return geminiModel;
        }
        return qwenModel;
    }

    /**
     * Get model by name.
     */
    public ChatModel getByName(String modelName) {
        ChatModel model = models.get(modelName);
        if (model == null) {
            log.warn("Model '{}' not found, using default", modelName);
            return geminiModel;
        }
        return model;
    }

    /**
     * Update user's model preference.
     */
    @Transactional
    public void setUserPreference(String userId, String modelName) {
        if (!models.containsKey(modelName)) {
            throw new IllegalArgumentException("Invalid model name: " + modelName + ". Available models: " + models.keySet());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setPreferredModel(modelName);
        userRepository.save(user);
        log.info("Updated model preference for user {}: {}", userId, modelName);
    }

    /**
     * Get user's current model preference.
     */
    public String getUserPreference(String userId) {
        return userRepository.findById(userId)
                .map(User::getPreferredModel)
                .orElse(DEFAULT_MODEL);
    }

    /**
     * Get all available model names.
     */
    public Set<String> getAvailableModels() {
        return models.keySet();
    }

    /**
     * Check if a specific model is available.
     */
    public boolean isModelAvailable(String modelName) {
        return models.containsKey(modelName);
    }

    /**
     * Check if Qwen model is available.
     */
    public boolean isQwenAvailable() {
        return qwenModel != null;
    }
}
