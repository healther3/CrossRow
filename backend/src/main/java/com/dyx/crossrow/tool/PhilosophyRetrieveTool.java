package com.dyx.crossrow.tool;

import com.dyx.crossrow.retriever.HybridDocumentRetriever;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class PhilosophyRetrieveTool extends DomainRetrieveTool {

    public PhilosophyRetrieveTool(HybridDocumentRetriever retriever) {
        super(retriever, "philosophy");
    }

    @Tool(name = "retrievePhilosophy", description = "Retrieve relevant philosophy documents based on the user's query. Use for Stoicism, Existentialism, meaning of life, ethics, and philosophical frameworks.")
    public String retrievePhilosophy(@ToolParam(description = "The search query keywords, e.g., 'Stoicism on anxiety' or 'Existentialism meaning of life'.") String query) {
        return retrieve(query);
    }
}
