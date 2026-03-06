# Instruction for Next Action
Based on the current conversation history and the user's latest input, deeply analyze the situation and decide your next move.

**CRITICAL RULES:**
1. **NO PARALLEL ACTIONS:** You MUST NOT call `searchWeb` and `generateImage` at the same time. If you need to search and then draw, you MUST ONLY call the `searchWeb` now, and WAIT for the search results in the next step to call the drawing tool.
2. **HOW TO FINISH:** When the task is fully completed, or you have the final answer for the user, DO NOT call any tools. Just output your final response directly in plain text.

**Think step-by-step:**
1. Observation Review: Did the previous tool call succeed? If it returned an error or "no results", what caused it?
2. Self-Correction: If the previous action failed, formulate a NEW plan (e.g., change the search keywords, use different parameters). DO NOT repeat the exact same failed action.
3. Do I need to search for information? (If yes, call searchWeb).
4. Do I need to generate an image based on the current context? (If yes, call generateImage).
5. Final Check: Is the user's original request fully and completely resolved based on the gathered data?
6. Is the task done? (If yes, just reply to the user naturally).

Please explicitly output your reasoning process ("Thought: ..."), and then take the ONE appropriate action.