package com.dyx.crossrow.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.ai.vertexai.gemini.schema.JsonSchemaConverter;

import java.util.Arrays;
import java.util.List;

@Slf4j
public class SimpleToolCallManager implements ToolCallingManager {
    private final ToolCallingManager delegate; // Spring AI 原生的默认管理器
    private final ToolCallback[] defaultTools;  // 默认工具集（兜底用）

    public SimpleToolCallManager(ToolCallingManager delegate, ToolCallback[] defaultTools) {
        this.delegate = delegate;
        this.defaultTools = defaultTools;
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        List<ToolDefinition> toolDefinitions = this.delegate.resolveToolDefinitions(chatOptions);
        return toolDefinitions.stream().map((td) -> {
            ObjectNode jsonSchema = JsonSchemaConverter.fromJson(td.inputSchema());
            ObjectNode openApiSchema = JsonSchemaConverter.convertToOpenApiSchema(jsonSchema);
            JsonSchemaGenerator.convertTypeValuesToUpperCase(openApiSchema);
            return DefaultToolDefinition.builder().name(td.name()).description(td.description()).inputSchema(openApiSchema.toPrettyString()).build();
        }).toList();
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse toolCallResponse) {
        // 优先从 Prompt 的 Options 中获取工具集（支持动态工具）
        ToolCallback[] toolsToUse = extractToolCallbacksFromPrompt(prompt);
        
        if (toolsToUse == null || toolsToUse.length == 0) {
            // 兜底：使用默认工具集
            toolsToUse = this.defaultTools;
            log.debug("Using default tools for execution");
        } else {
            log.debug("Using dynamic tools from prompt options");
        }

        log.info("🔧 SimpleToolCallManager: 正在为 Act 阶段挂载工具: {}",
                Arrays.stream(toolsToUse).map(tc -> tc.getToolDefinition().name()).toList());

        VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                .toolCallbacks(toolsToUse)
                .build();

        // 创建一个新的 Prompt，保留原消息，但注入工具 Options
        Prompt promptWithTools = new Prompt(prompt.getInstructions(), options);

        // 将武装好的 Prompt 交给原生管理器去执行
        return delegate.executeToolCalls(promptWithTools, toolCallResponse);
    }

    /**
     * 支持动态工具集的执行方法（重载版本）
     * @param prompt 原始 Prompt
     * @param toolCallResponse LLM 返回的工具调用响应
     * @param dynamicTools 动态指定的工具集
     * @return 工具执行结果
     */
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse toolCallResponse, ToolCallback[] dynamicTools) {
        ToolCallback[] toolsToUse = (dynamicTools != null && dynamicTools.length > 0) ? dynamicTools : this.defaultTools;

        log.info(" SimpleToolCallManager: 正在为 Act 阶段挂载工具(动态): {}",
                Arrays.stream(toolsToUse).map(tc -> tc.getToolDefinition().name()).toList());

        VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                .toolCallbacks(toolsToUse)
                .build();

        Prompt promptWithTools = new Prompt(prompt.getInstructions(), options);
        return delegate.executeToolCalls(promptWithTools, toolCallResponse);
    }

    /**
     * 从 Prompt 的 Options 中提取 ToolCallbacks
     */
    private ToolCallback[] extractToolCallbacksFromPrompt(Prompt prompt) {
        if (prompt.getOptions() instanceof VertexAiGeminiChatOptions geminiOptions) {
            List<ToolCallback> callbacks = geminiOptions.getToolCallbacks();
            if (callbacks != null && !callbacks.isEmpty()) {
                return callbacks.toArray(new ToolCallback[0]);
            }
        }
        return null;
    }
}
