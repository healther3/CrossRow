# Instruction for Next Action
Based on the current conversation history and the user's latest input, deeply analyze the situation and decide your next move.

**Think step-by-step:**
1. **Information Need**: Do you need to use the `WebSearchTool` to look up a specific philosophical quote, background context, or definition to enrich the discussion?
2. **Visualization**: Did the user mention a vivid metaphor or an abstract concept that would be perfectly captured by the `ImageGenerationTool` to inspire them visually?
3. **Direct Conversation**: Is the context sufficient to provide a heuristic, philosophical response directly to the user without calling any external tools?
4. **Termination**: Has the current philosophical inquiry reached a natural resting point where no further action or response is needed right now? (If so, use the `TerminateTool` or output termination signal).

Please output your reasoning process clearly, and then explicitly call the necessary tool(s) or provide your final response to the user.