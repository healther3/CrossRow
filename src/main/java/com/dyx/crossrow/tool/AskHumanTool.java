package com.dyx.crossrow.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class AskHumanTool {
    @Tool(name = "askHuman", description = """
        CRITICAL: Use this tool to ask the human user for clarification BEFORE taking action when:
        1. The user's request is AMBIGUOUS (e.g., "help me with that" - what is "that"?)
        2. The user's request has MULTIPLE VALID INTERPRETATIONS (e.g., "make it better" - better how?)
        3. You need SPECIFIC PREFERENCES the user hasn't provided (e.g., style, format, scope)
        4. The action is IRREVERSIBLE or HIGH-STAKES and you want confirmation
        5. You are UNCERTAIN about the user's true intent
        
        DO NOT GUESS. When in doubt, ASK. A clarifying question is always better than a wrong assumption.
        Examples of when to use:
        - User says "search for news" -> Ask: "What topic would you like me to search for?"
        - User says "generate an image" -> Ask: "What scene or concept would you like me to visualize?"
        - User mentions "the problem" without context -> Ask: "Could you clarify which problem you're referring to?"
        """)
    public String askHuman(
            @ToolParam(description = "A clear, specific question to ask the user. Be concise but include context about why you're asking.") 
            String question) {
        return String.format("<hidden_action type='ask_human' question='%s' />\n(Waiting for user input...)", question);
    }
}
