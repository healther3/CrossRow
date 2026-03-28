package com.dyx.crossrow.tool;

import lombok.extern.slf4j.Slf4j;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.regex.Pattern;

@Slf4j
public class CalculatorTool {

    private static final Pattern SAFE_EXPRESSION = Pattern.compile(
            "^[0-9+\\-*/%^().,'\\s" +
            "a-z" +   // function names like sqrt, sin, log...
            "A-Z" +   // constants like PI, E
            "]*$"
    );

    private static final int MAX_EXPRESSION_LENGTH = 500;

    @Tool(
            name = "calculator",
            description = "Evaluates mathematical expressions safely. Supports: " +
                    "basic arithmetic (+, -, *, /, %, ^), " +
                    "functions (sqrt, abs, sin, cos, tan, log, log2, log10, exp, ceil, floor, " +
                    "asin, acos, atan, sinh, cosh, tanh, cbrt), " +
                    "and constants (pi, e). " +
                    "Examples: '15.5 * (2 + 3)', 'sqrt(144)', '2^10', 'sin(pi/2)', 'log10(1000)'. " +
                    "ALWAYS use this tool for any math operations to avoid calculation errors."
    )
    public String calculate(
            @ToolParam(description = "The mathematical expression to evaluate, e.g. '15.5 * (2 + 3)' or 'sqrt(144)'.")
            String expression) {

        if (expression == null || expression.isBlank()) {
            return "Error: Expression cannot be empty.";
        }

        String trimmed = expression.trim();

        if (trimmed.length() > MAX_EXPRESSION_LENGTH) {
            return "Error: Expression too long (max " + MAX_EXPRESSION_LENGTH + " characters).";
        }

        if (!SAFE_EXPRESSION.matcher(trimmed).matches()) {
            return "Error: Expression contains invalid characters. Only numbers, operators (+, -, *, /, %, ^), " +
                    "parentheses, and math function names are allowed.";
        }

        try {
            log.info("Agent calculating: {}", trimmed);

            Expression exp = new ExpressionBuilder(trimmed).build();
            double result = exp.evaluate();

            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return "Error: Calculation resulted in " + (Double.isNaN(result) ? "NaN (undefined)" : "Infinity") +
                        ". Please check the expression.";
            }

            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return "Result: " + (long) result;
            }
            return "Result: " + result;

        } catch (Exception e) {
            log.error("Agent calculation failed for: {}", trimmed, e);
            return "Error: Could not evaluate the expression. " +
                    "Please ensure it is a valid math formula (e.g. '15.5 * (2 + 3)' or 'sqrt(144)').";
        }
    }
}
