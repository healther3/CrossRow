package com.dyx.crossrow.config;

import com.dyx.crossrow.rag.CrossRowDocumentLoader;
import com.google.genai.Documents;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

//@Configuration
public class VectorStoreConfiguration {

    @Resource
    private CrossRowDocumentLoader crossRowDocumentLoader;

    @Bean
    VectorStore CrossRowVectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel)
                .build();
        List<Document> documents = crossRowDocumentLoader.loadMarkDownFiles();
        for (int i = 0; i < documents.size(); i += 2) {
            int end = Math.min(documents.size(), i + 2);
            List<Document> batch = documents.subList(i, end);

            try {
                simpleVectorStore.add(batch);
                // 关键：每批次处理完，强制休息 2 秒，避免触发 Google 的速率限制
                Thread.sleep(4000);
            } catch (Exception e) {
                // 简单的重试逻辑，或者记录日志
                System.err.println("Batch failed, waiting longer...");
                try {
                    Thread.sleep(30000);
                    simpleVectorStore.add(batch);
                } catch (Exception ex) {
                    System.err.println("Batch failed, waiting longer also failed.");
                }
            }
        }
        return simpleVectorStore;
    }
}

