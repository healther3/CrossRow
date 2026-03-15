package com.dyx.crossrow.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * SSE 流式返回的单步结果，包含思考过程和工具调用信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StepResultDTO {
    
    /**
     * 步骤类型: "thinking", "tool_call", "final_answer", "error"
     */
    private String stepType;
    
    /**
     * 当前步骤编号
     */
    private Integer stepNumber;
    
    /**
     * 思考内容（LLM 的推理过程）
     */
    private String thinking;
    
    /**
     * 工具调用信息列表
     */
    private List<ToolCallInfo> toolCalls;
    
    /**
     * 最终回答（当不需要工具调用时）
     */
    private String finalAnswer;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * Token 使用统计
     */
    private TokenUsage tokenUsage;
    
    /**
     * 步骤总耗时（毫秒）
     */
    private Long elapsedMs;
    
    /**
     * 工具调用详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCallInfo {
        /**
         * 工具名称
         */
        private String toolName;
        
        /**
         * 工具参数（JSON 字符串）
         */
        private String arguments;
        
        /**
         * 工具执行结果
         */
        private String result;
        
        /**
         * 工具执行状态: "pending", "success", "error"
         */
        private String status;
        
        /**
         * 工具执行耗时（毫秒）
         */
        private Long elapsedMs;
    }
    
    /**
     * Token 使用统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TokenUsage {
        /**
         * 输入 token 数量
         */
        private Long promptTokens;
        
        /**
         * 输出 token 数量
         */
        private Long completionTokens;
        
        /**
         * 总 token 数量
         */
        private Long totalTokens;
        
        /**
         * 估算成本（美元）
         */
        private BigDecimal estimatedCostUsd;
    }
}
