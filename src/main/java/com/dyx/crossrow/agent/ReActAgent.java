package com.dyx.crossrow.agent;

import com.dyx.crossrow.exceptions.ReActProcessingException;
import com.dyx.crossrow.model.dto.StepResultDTO;
import com.dyx.crossrow.utils.TokenCostCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;

import java.util.List;

/**
 * loop: Reasoning and Acting
 */
@Slf4j
public abstract class ReActAgent extends BaseAgent{
    /**
     * execute current state and decide next step
     * @return true: act ; false: reasoning
      */
    public abstract boolean thinking();

    /**
     * execute the action;including tool calling
     * @return the results of execution
     */
    public abstract String act();

    /**
     * 获取最近一次思考的内容（子类可重写）
     * @return 思考内容，如果没有则返回 null
     */
    protected String getThinkingResult() {
        return null;
    }
    
    /**
     * 获取待执行的工具调用信息（子类可重写）
     * @return 工具调用信息列表
     */
    protected List<StepResultDTO.ToolCallInfo> getPendingToolCallInfos() {
        return null;
    }
    
    /**
     * 获取当前步骤的 token 使用信息（子类可重写）
     * @return Usage 对象
     */
    protected Usage getCurrentUsage() {
        return null;
    }

    @Override
    public String step() {
        try{
        // thinking:
        boolean shouldAct = thinking();
        if (!shouldAct) {
            // 尝试获取 LLM 的实际回答内容
            String thinkingResult = getThinkingResult();
            if (thinkingResult != null && !thinkingResult.isEmpty()) {
                return thinkingResult;
            }
            return "reasoning completed";
        }
        // acting
        return act();
        } catch (Exception e){
            throw new ReActProcessingException();
        }
    }
    
    /**
     * 执行单步并通过回调发送实时状态更新
     * @param stepNumber 当前步骤编号
     * @param onEvent 事件回调，用于发送 pending 状态
     * @return 最终的步骤结果
     */
    public StepResultDTO stepWithCallback(int stepNumber, StepEventCallback onEvent) {
        long startTime = System.currentTimeMillis();
        
        try {
            // thinking phase
            boolean shouldAct = thinking();
            String thinkingContent = getThinkingResult();
            
            // 调试日志：确认 thinkingContent 的值
            log.info("=== stepWithCallback Debug ===");
            log.info("shouldAct: {}", shouldAct);
            log.info("thinkingContent: '{}'", thinkingContent);
            log.info("thinkingContent is null: {}", thinkingContent == null);
            log.info("thinkingContent length: {}", thinkingContent != null ? thinkingContent.length() : -1);
            log.info("==============================");
            
            // 获取 token 使用信息
            StepResultDTO.TokenUsage tokenUsage = TokenCostCalculator.calculateTokenUsage(getCurrentUsage(), "gemini");
            
            if (!shouldAct) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (thinkingContent != null && !thinkingContent.isEmpty()) {
                    return StepResultDTO.builder()
                            .stepType("final_answer")
                            .stepNumber(stepNumber)
                            .thinking(thinkingContent)
                            .finalAnswer(thinkingContent)
                            .tokenUsage(tokenUsage)
                            .elapsedMs(elapsed)
                            .build();
                }
                return StepResultDTO.builder()
                        .stepType("thinking")
                        .stepNumber(stepNumber)
                        .thinking("reasoning completed")
                        .tokenUsage(tokenUsage)
                        .elapsedMs(elapsed)
                        .build();
            }
            
            // 获取待执行的工具调用信息（pending 状态）
            List<StepResultDTO.ToolCallInfo> toolCallInfos = getPendingToolCallInfos();
            
            // 发送 pending 状态
            log.info("Before pending: thinkingContent = '{}'", thinkingContent);
            if (onEvent != null && toolCallInfos != null && !toolCallInfos.isEmpty()) {
                StepResultDTO pendingResult = StepResultDTO.builder()
                        .stepType("tool_call")
                        .stepNumber(stepNumber)
                        .thinking(thinkingContent)
                        .toolCalls(toolCallInfos)
                        .tokenUsage(tokenUsage)
                        .build();
                log.info("Pending result thinking: '{}'", pendingResult.getThinking());
                onEvent.onStepEvent(pendingResult);
            }
            
            log.info("After pending, before act: thinkingContent = '{}'", thinkingContent);
            
            // acting phase - 执行工具调用
            long toolStartTime = System.currentTimeMillis();
            String actResult = act();
            long toolElapsed = System.currentTimeMillis() - toolStartTime;
            
            log.info("After act: thinkingContent = '{}'", thinkingContent);
            
            // 更新工具调用结果
            if (toolCallInfos != null && !toolCallInfos.isEmpty()) {
                updateToolCallResults(toolCallInfos, actResult, toolElapsed);
            }
            
            long totalElapsed = System.currentTimeMillis() - startTime;
            
            // 调试：确认返回给前端的 thinking 值
            log.info("=== Building final tool_call result ===");
            log.info("thinkingContent before build: '{}'", thinkingContent);
            
            StepResultDTO result = StepResultDTO.builder()
                    .stepType("tool_call")
                    .stepNumber(stepNumber)
                    .thinking(thinkingContent)
                    .toolCalls(toolCallInfos)
                    .tokenUsage(tokenUsage)
                    .elapsedMs(totalElapsed)
                    .build();
            
            log.info("result.getThinking(): '{}'", result.getThinking());
            log.info("========================================");
            
            return result;
                    
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            return StepResultDTO.builder()
                    .stepType("error")
                    .stepNumber(stepNumber)
                    .error(e.getMessage())
                    .elapsedMs(elapsed)
                    .build();
        }
    }
    
    /**
     * 步骤事件回调接口
     */
    @FunctionalInterface
    public interface StepEventCallback {
        void onStepEvent(StepResultDTO event);
    }
    
    /**
     * 解析 act() 结果并更新工具调用信息
     */
    private void updateToolCallResults(List<StepResultDTO.ToolCallInfo> toolCallInfos, String actResult, long toolElapsed) {
        if (actResult == null || toolCallInfos == null || toolCallInfos.isEmpty()) return;
        
        boolean isError = actResult.startsWith("fail") || actResult.contains("Error");
        String[] parts = actResult.split("\n", 2);
        String resultContent = parts.length > 1 ? parts[1] : actResult;
        
        StepResultDTO.ToolCallInfo toolInfo = toolCallInfos.get(0);
        toolInfo.setResult(resultContent);
        toolInfo.setStatus(isError ? "error" : "success");
        toolInfo.setElapsedMs(toolElapsed);
    }
}
