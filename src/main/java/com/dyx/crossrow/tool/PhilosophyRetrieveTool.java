package com.dyx.crossrow.tool;


import com.dyx.crossrow.retriever.HybridDocumentRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.stream.Collectors;

public class PhilosophyRetrieveTool {
    private static final Logger log = LoggerFactory.getLogger(PhilosophyRetrieveTool.class);
    private final HybridDocumentRetriever hybridDocumentRetriever;

    public PhilosophyRetrieveTool(HybridDocumentRetriever hybridDocumentRetriever) {
        this.hybridDocumentRetriever = hybridDocumentRetriever;
    }

    @Tool(name = "retrievePhilosophy", description = "Retrieve relevant philosophy documents based on the user's query.")
    public String retrievePhilosophy(@ToolParam(description = "The search query keywords, e.g.," +
            " 'Stoicism on anxiety' or 'Existentialism meaning of life'.") String query) {
        log.info("Tool calling: Searching philosophy knowledge base for: {}", query);
        try {
            List<Document> documents = hybridDocumentRetriever.retrieve(new Query(query));
            if (documents == null || documents.isEmpty()) {
                return "在RAG知识库中未找到关于 '" + query + "' 的相关内容。";
            }
                return documents.stream()
                        .map(doc -> {
                            String filename = (String) doc.getMetadata().getOrDefault("filename", "Unknown Source");
                            return String.format("[filename: %s]\n%s", filename, doc.getText());
                        })
                        .collect(Collectors.joining("\n\n---------------------\n\n"));

        }catch(Exception e){
                log.error("rag search failed", e);
                return "检索RAG知识库时发生错误: " + e.getMessage();
        }
    }
}
