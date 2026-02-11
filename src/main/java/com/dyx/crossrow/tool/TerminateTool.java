package com.dyx.crossrow.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

public class TerminateTool {
    @Tool(description = "Terminate the agent execution with a final result")
    public String terminate(@ToolParam(description = "The reason for termination") String reason) {
        return "Agent terminated: " + reason;
    }
}
