package com.dyx.crossrow.config;


import com.dyx.crossrow.rag.CrossRowDocumentLoader;
import com.dyx.crossrow.rag.DocumentCountBatchingStrategy;
import com.dyx.crossrow.rag.SimpleKeyWordEnricher;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
public class PgVectorConfiguration {
//    @Autowired
//    VectorStore vectorStore;
    @Resource
    private SimpleKeyWordEnricher simpleKeyWordEnricher;

    private static final int GEMINI_MAX_BATCH_SIZE = 10;

    @Bean
    public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, CrossRowDocumentLoader crossRowDocumentLoader) {
        // Vertex AI text-embedding-005 维度为 768
        return   PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(768)
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("my_custom_pg_vectors")
                .batchingStrategy(new DocumentCountBatchingStrategy(10))
                .maxDocumentBatchSize(10)
                .build();

    }

        @Bean
        @org.springframework.context.annotation.Profile("!test")  // 测试时不运行
        public ApplicationRunner documentLoader(VectorStore pgVectorStore, CrossRowDocumentLoader crossRowDocumentLoader) {
            return args -> {
                List<Document> documents = crossRowDocumentLoader.loadMarkDownFiles();
                List<Document> enrichedDocuments = simpleKeyWordEnricher.enrichDocuments(documents);
                pgVectorStore.add(enrichedDocuments);
                    System.out.println("文档加载完成，共 " + documents.size() + " 条");
            };
        }
}
