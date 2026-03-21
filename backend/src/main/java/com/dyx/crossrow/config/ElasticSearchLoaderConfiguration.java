package com.dyx.crossrow.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.dyx.crossrow.elasticsearch.CrossRowDocument;
import com.dyx.crossrow.rag.CrossRowDocumentLoader;
import com.dyx.crossrow.rag.SimpleKeyWordEnricher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.IntStream;

@Configuration
public class ElasticSearchLoaderConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ElasticSearchLoaderConfiguration.class);

    @Bean
    @org.springframework.context.annotation.Profile("!test")
    public ApplicationRunner esDocumentLoader(
            ElasticsearchClient esClient,
            EmbeddingModel embeddingModel,
            CrossRowDocumentLoader loader,
            SimpleKeyWordEnricher enricher) {

        return args -> {
            // 1. 检查 ES 是否已有数据 (避免重复消耗配额)
            for (String domain : List.of("philosophy","psychology","sociology")) {
                String indexName = domain + "_docs";
                long count = esClient.count(c -> c.index(indexName)).count();
                if (count > 0) {
                    log.info("ElasticSearch: {} 中已有 {} 条数据，跳过初始化。", indexName, count);
                    continue;
                }
                log.info("初始化: {}", indexName);
                loadAndIndexDocuments(domain, indexName, esClient, embeddingModel, loader, enricher);
            }
            log.info("初始化完成");
        };
    }

    public void loadAndIndexDocuments(String domain, String indexName,
                                      ElasticsearchClient esClient,
                                      EmbeddingModel embeddingModel,
                                      CrossRowDocumentLoader loader,
                                      SimpleKeyWordEnricher enricher) {
        // 2. 加载文档
        log.info(" LOAD ElasticSearch 加载 {} 数据...", domain);
        List<Document> docs = loader.loadMarkDownFiles(domain);
        List<Document> enrichedDocs = enricher.enrichDocuments(docs);

        // 3. 循环处理：Embedding + 写入 ES
        for (Document doc : enrichedDocs) {
            try {
                // A. 调用 Google 生成向量 (这里消耗配额)
                float[] embedding = embeddingModel.embed(doc.getText());

                // B. 存入 ES
                CrossRowDocument esDoc = new CrossRowDocument();
                esDoc.setId(doc.getId());
                esDoc.setContent(doc.getText());
                esDoc.setMetadata(doc.getMetadata());
                esDoc.setEmbedding(IntStream.range(0, embedding.length)
                        .mapToObj(i -> embedding[i])
                        .toList());

                esClient.index(i -> i
                        .index(indexName)
                        .id(esDoc.getId())
                        .document(esDoc)
                );

                log.info("已索引:{}:{} ",indexName,doc.getId());
                Thread.sleep(2000); // 保护配额

            } catch (Exception e) {
                log.error("索引失败: {}" ,e.getMessage());
            }
        }
    }
}
