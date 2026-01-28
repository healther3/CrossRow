package com.dyx.crossrow.config;

import com.dyx.crossrow.rag.CrossRowDocumentLoader;
import jakarta.annotation.Resource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfiguration {

    @Resource
    private CrossRowDocumentLoader crossRowDocumentLoader;

    @Bean
    VectorStore CrossRowVectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore simpleVectorStore= SimpleVectorStore.builder(embeddingModel)
                .build();
        simpleVectorStore.add(crossRowDocumentLoader.loadMarkDownFiles());
        return simpleVectorStore;
    }
}
