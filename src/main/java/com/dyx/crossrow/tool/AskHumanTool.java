package com.dyx.crossrow.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class AskHumanTool {
    @Tool(name = "askHuman", description = "Use this tool to ask the human user for help, confirmation, or specific preferences when you are unsure how to proceed. Stop guessing and just ask.")
    public String askHuman(@ToolParam(description = "The specific question you want to ask the user.") String question) {
        return String.format("<hidden_action type='ask_human' question='%s' />\n(Waiting for user input...)", question);
    }
}
