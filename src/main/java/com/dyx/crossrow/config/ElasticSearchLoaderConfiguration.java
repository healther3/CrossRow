package com.dyx.crossrow.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.dyx.crossrow.elasticsearch.CrossRowDocument;
import com.dyx.crossrow.rag.CrossRowDocumentLoader;
import com.dyx.crossrow.rag.SimpleKeyWordEnricher;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ElasticSearchLoaderConfiguration {
    @Bean
    @org.springframework.context.annotation.Profile("!test")
    public ApplicationRunner esDocumentLoader(
            ElasticsearchClient esClient,
            EmbeddingModel embeddingModel,
            CrossRowDocumentLoader loader,
            SimpleKeyWordEnricher enricher) {

        return args -> {
            // 1. 检查 ES 是否已有数据 (避免重复消耗配额)
            long count = esClient.count(c -> c.index("philosophy_docs")).count();
            if (count > 0) {
                System.out.println("ElasticSearch 中已有 " + count + " 条数据，跳过初始化。");
                return;
            }

            // 2. 加载文档
            System.out.println(" 开始为 ElasticSearch 加载数据...");
            List<Document> docs = loader.loadMarkDownFiles();
            List<Document> enrichedDocs = enricher.enrichDocuments(docs);

            // 3. 循环处理：Embedding + 写入 ES
            // 这里依然建议使用之前提到的“单条处理+休眠”策略来保护配额
            for (Document doc : enrichedDocs) {
                try {
                    // A. 调用 Google 生成向量 (这里消耗配额)
                    float[] embedding = embeddingModel.embed(doc.getText());

                    // B. 存入 ES
                    CrossRowDocument esDoc = new CrossRowDocument();
                    esDoc.setId(doc.getId());
                    esDoc.setContent(doc.getText());
                    esDoc.setMetadata(doc.getMetadata());
                    // 假设你的 CrossRowDocument 有个字段存向量，比如 setEmbedding(List<Float>...)
                    // esDoc.setEmbedding(toList(embedding));

                    esClient.index(i -> i
                            .index("philosophy_docs")
                            .id(esDoc.getId())
                            .document(esDoc)
                    );

                    System.out.println("已索引: " + doc.getId());
                    Thread.sleep(2000); // 保护配额

                } catch (Exception e) {
                    System.err.println("索引失败: " + e.getMessage());
                }
            }
            System.out.println("ElasticSearch 数据加载完成！");
        };
    }
}
