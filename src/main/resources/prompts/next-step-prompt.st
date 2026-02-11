# Instruction for Next Action
Based on the current conversation history and the user's latest input, deeply analyze the situation and decide your next move.

**CRITICAL RULES:**
1. **NO PARALLEL ACTIONS:** You MUST NOT call `WebSearchTool` and `ImageGenerationTool` at the same time. If you need to search and then draw, you MUST ONLY call the `WebSearchTool` now, and WAIT for the search results in the next step to call the drawing tool.
2. **HOW TO FINISH:** When the task is fully completed, or you have the final answer for the user, DO NOT call any tools. Just output your final response directly in plain text.

**Think step-by-step:**
1. Do I need to search for information? (If yes, call WebSearchTool).
2. Do I need to generate an image based on the current context? (If yes, call ImageGenerationTool).
3. Is the task done? (If yes, just reply to the user naturally).

Please explicitly output your reasoning process ("Thought: ..."), and then take the ONE appropriate action.