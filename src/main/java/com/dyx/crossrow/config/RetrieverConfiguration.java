package com.dyx.crossrow.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.dyx.crossrow.retriever.HybridDocumentRetriever;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 *  RETRIEVER BEANS FOR DIFFERENT DOMAINS
 */
@Configuration
public class RetrieverConfiguration {

    @Bean
    @Primary
    public HybridDocumentRetriever philosophyRetriever(ElasticsearchClient esClient, EmbeddingModel embeddingModel) {
        return new HybridDocumentRetriever(esClient, embeddingModel, "philosophy_docs");
    }

    @Bean
    public HybridDocumentRetriever psychologyRetriever(ElasticsearchClient esClient, EmbeddingModel embeddingModel) {
        return new HybridDocumentRetriever(esClient, embeddingModel, "psychology_docs");
    }

    @Bean
    public HybridDocumentRetriever sociologyRetriever(ElasticsearchClient esClient, EmbeddingModel embeddingModel) {
        return new HybridDocumentRetriever(esClient, embeddingModel, "sociology_docs");
    }
}
