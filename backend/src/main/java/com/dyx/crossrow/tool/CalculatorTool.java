package com.dyx.crossrow.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

@Slf4j
public class CalculatorTool {

    private final ExpressionParser parser;

    public CalculatorTool() {
        // 初始化 Spring 表达式解析器
        this.parser = new SpelExpressionParser();
    }

    @Tool(
            name = "calculator",
            description = "Evaluates mathematical expressions. ALWAYS use this tool for any math operations (addition, subtraction, multiplication, division) to avoid calculation errors. Input MUST be a valid math string (e.g., '15.5 * (2 + 3)')."
    )
    public String calculate(
            @ToolParam(description = "The mathematical expression to evaluate.")
            String expression) {

        try {
            log.info("Agent calculating: {}", expression);

            // 1. 执行数学表达式的计算
            Object result = parser.parseExpression(expression).getValue();

            // 2. 将结果返回给 Agent
            return "Result: " + result.toString();

        } catch (Exception e) {
            log.error("Agent calculation failed for: {}", expression, e);
            // 给 Agent 返回明确的错误信息，引导它修正表达式
            return "Error: Invalid mathematical expression. Please ensure you are sending a standard math formula.";
        }
    }
}