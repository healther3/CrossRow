package com.dyx.crossrow.tool;

import com.dyx.crossrow.retriever.HybridDocumentRetriever;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class PsychologyRetrieveTool extends DomainRetrieveTool {

    public PsychologyRetrieveTool(HybridDocumentRetriever retriever) {
        super(retriever, "psychology");
    }

    @Tool(name = "retrievePsychology", description = "Retrieve relevant psychology documents based on the user's query. Use for cognitive patterns, emotional regulation, attachment theory, mental health concepts.")
    public String retrievePsychology(@ToolParam(description = "The search query keywords, e.g., 'cognitive distortions', 'attachment styles', 'anxiety management'.") String query) {
        return retrieve(query);
    }
}
