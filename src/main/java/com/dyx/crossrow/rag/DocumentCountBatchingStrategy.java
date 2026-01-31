package com.dyx.crossrow.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;

import java.util.ArrayList;
import java.util.List;

public class DocumentCountBatchingStrategy  implements BatchingStrategy {
    private final int maxDocumentsPerBatch;

    public DocumentCountBatchingStrategy(int maxDocumentsPerBatch) {
        this.maxDocumentsPerBatch = maxDocumentsPerBatch;
    }

    @Override
    public List<List<Document>> batch(List<Document> documents) {
        List<List<Document>> batches = new ArrayList<>();
        for (int i = 0; i < documents.size(); i += maxDocumentsPerBatch) {
            int end = Math.min(i + maxDocumentsPerBatch, documents.size());
            batches.add(documents.subList(i, end));
        }
        return batches;
    }
}
