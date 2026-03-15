package com.dyx.crossrow.utils;

import com.dyx.crossrow.model.dto.StepResultDTO;
import org.springframework.ai.chat.metadata.Usage;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Token 成本计算器
 * 根据不同模型的官方定价计算 API 调用成本
 * 
 * 定价（2026年3月）：
 * - Gemini 2.5 Flash: Input $0.30/M, Output $2.50/M
 * - Qwen Turbo: Input $0.05/M, Output $0.20/M
 */
public class TokenCostCalculator {
    
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    
    // Gemini 定价
    private static final BigDecimal GEMINI_INPUT_PRICE = BigDecimal.valueOf(0.30);
    private static final BigDecimal GEMINI_OUTPUT_PRICE = BigDecimal.valueOf(2.50);
    
    // Qwen 定价
    private static final BigDecimal QWEN_INPUT_PRICE = BigDecimal.valueOf(0.05);
    private static final BigDecimal QWEN_OUTPUT_PRICE = BigDecimal.valueOf(0.20);
    
    /**
     * 根据 Usage 和模型名称计算 TokenUsage DTO
     */
    public static StepResultDTO.TokenUsage calculateTokenUsage(Usage usage, String modelName) {
        if (usage == null) {
            return null;
        }
        
        long promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        long completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        long totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : promptTokens + completionTokens;
        
        BigDecimal cost = calculateCost(promptTokens, completionTokens, modelName);
        
        return StepResultDTO.TokenUsage.builder()
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .estimatedCostUsd(cost)
                .build();
    }
    
    /**
     * 计算成本
     */
    private static BigDecimal calculateCost(long promptTokens, long completionTokens, String modelName) {
        boolean isQwen = modelName != null && modelName.toLowerCase().contains("qwen");
        
        BigDecimal inputPrice = isQwen ? QWEN_INPUT_PRICE : GEMINI_INPUT_PRICE;
        BigDecimal outputPrice = isQwen ? QWEN_OUTPUT_PRICE : GEMINI_OUTPUT_PRICE;
        
        BigDecimal inputCost = BigDecimal.valueOf(promptTokens)
                .multiply(inputPrice)
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
        
        BigDecimal outputCost = BigDecimal.valueOf(completionTokens)
                .multiply(outputPrice)
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
        
        return inputCost.add(outputCost);
    }
}
