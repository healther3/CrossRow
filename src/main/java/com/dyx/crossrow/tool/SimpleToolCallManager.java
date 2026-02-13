package com.dyx.crossrow.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

public class SimpleToolCallManager implements ToolCallingManager {
    private final ToolCallingManager delegate; // Spring AI 原生的默认管理器
    private final ToolCallback[] allTools;  // 在 ToolRegister 里注册的所有工具

    public SimpleToolCallManager(ToolCallingManager delegate, ToolCallback[] allTools) {
        this.delegate = delegate;
        this.allTools = allTools;
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
        // 1. 打印日志，确认工具已就位
         System.out.println("🔧 CrossRowToolManager: 正在为 Act 阶段挂载工具: " +
                 Arrays.stream(allTools).map(ToolCallback::getToolDefinition).toList());
        VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                .toolCallbacks(this.allTools)
                .build();
        // 3. 创建一个新的 Prompt，保留原消息，但注入了我们的工具 Options
        Prompt promptWithTools = new Prompt(prompt.getInstructions(), options);

        // 4. 将武装好的 Prompt 交给原生管理器去执行
        return delegate.executeToolCalls(promptWithTools, toolCallResponse);
    }
}
