package com.dyx.crossrow.tool;

import com.dyx.crossrow.retriever.HybridDocumentRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DomainRetrieveTool {
    private final HybridDocumentRetriever retriever;
    private final String domain;

    public DomainRetrieveTool(HybridDocumentRetriever retriever, String domain) {
        this.retriever = retriever;
        this.domain = domain;
    }

    public String retrieve(String query) {
        log.info("Tool calling: Searching {} knowledge base for: {}", domain, query);
        try {
            List<Document> documents = retriever.retrieve(new Query(query));
            if (documents == null || documents.isEmpty()) {
                return "No relevant content found in " + domain + " knowledge base for: '" + query + "'";
            }
            return documents.stream()
                    .map(doc -> {
                        String filename = (String) doc.getMetadata().getOrDefault("filename", "Unknown Source");
                        return String.format("[source: %s]\n%s", filename, doc.getText());
                    })
                    .collect(Collectors.joining("\n\n---------------------\n\n"));
        } catch (Exception e) {
            log.error("{} RAG search failed", domain, e);
            return "Error searching " + domain + " knowledge base: " + e.getMessage();
        }
    }

}
