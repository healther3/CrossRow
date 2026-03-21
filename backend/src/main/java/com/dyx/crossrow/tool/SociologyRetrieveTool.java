package com.dyx.crossrow.tool;

import com.dyx.crossrow.retriever.HybridDocumentRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SociologyRetrieveTool {
    private final HybridDocumentRetriever retriever;

    public SociologyRetrieveTool(HybridDocumentRetriever retriever) {
        this.retriever = retriever;
    }

    @Tool(name = "retrieveSociology", description = "Retrieve relevant sociology documents based on the user's query. Use for social structures, power dynamics, cultural analysis, institutional critique.")
    public String retrieveSociology(@ToolParam(description = "The search query keywords, e.g., 'alienation Marx', 'cultural capital Bourdieu', 'anomie Durkheim'.") String query) {
        log.info("Tool calling: Searching sociology knowledge base for: {}", query);
        try {
            List<Document> documents = retriever.retrieve(new Query(query));
            if (documents == null || documents.isEmpty()) {
                return "No relevant content found in sociology knowledge base for: '" + query + "'";
            }
            return documents.stream()
                    .map(doc -> {
                        String filename = (String) doc.getMetadata().getOrDefault("filename", "Unknown Source");
                        return String.format("[source: %s]\n%s", filename, doc.getText());
                    })
                    .collect(Collectors.joining("\n\n---------------------\n\n"));
        } catch (Exception e) {
            log.error("Sociology RAG search failed", e);
            return "Error searching sociology knowledge base: " + e.getMessage();
        }
    }
}
