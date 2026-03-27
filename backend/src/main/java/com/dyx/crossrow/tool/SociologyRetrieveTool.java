package com.dyx.crossrow.tool;

import com.dyx.crossrow.retriever.HybridDocumentRetriever;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class SociologyRetrieveTool extends DomainRetrieveTool {

    public SociologyRetrieveTool(HybridDocumentRetriever retriever) {
        super(retriever, "sociology");
    }

    @Tool(name = "retrieveSociology", description = "Retrieve relevant sociology documents based on the user's query. Use for social structures, power dynamics, cultural analysis, institutional critique.")
    public String retrieveSociology(@ToolParam(description = "The search query keywords, e.g., 'alienation Marx', 'cultural capital Bourdieu', 'anomie Durkheim'.") String query) {
        return retrieve(query);
    }
}
