package com.dyx.crossrow.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.dyx.crossrow.properties.ElasticsearchProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
@Component
public class ElasticsearchDocumentStore {

    private final ElasticsearchIndexManager elasticsearchIndexManager;
    private final ElasticsearchClient esClient;
    private final EmbeddingModel embeddingModel;
    private final ElasticsearchProperties properties;

    public ElasticsearchDocumentStore(ElasticsearchIndexManager elasticsearchIndexManager, ElasticsearchClient esClient, EmbeddingModel embeddingModel, ElasticsearchProperties properties) {
        this.elasticsearchIndexManager = elasticsearchIndexManager;
        this.esClient = esClient;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    /**
     * 批量存储文档
     */
    public void storeAll(List<Document> documents) throws IOException {
        // get text content
        List<String> contents = documents.stream()
                .map(Document::getText)
                .toList();
        //embedding text into 1024 dim vectors
        List<float[]> embeddings = embeddingModel.embed(contents);
        //create bulk request
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            float[] embedding = embeddings.get(i);
            // convert float array into list
            List<Float> floatList = IntStream.range(0, embedding.length)
                    .mapToObj(j -> embedding[j])
                    .toList();

            // 转换为自定义文件格式
            CrossRowDocument record = convertToCrossRowDocument(doc, floatList);

            // 添加到 Bulk 请求
            bulkBuilder.operations(op -> op
                    .index(idx -> idx
                            .index(properties.getIndexName())
                            .id(record.getId())
                            .document(record)
                    )
            );

        }

        // 3. 执行批量索引
        BulkResponse response = esClient.bulk(bulkBuilder.build());

        // 4. 检查结果
        if (response.errors()) {
            log.error("批量索引存在错误");
            // 处理错误...
        } else {
            log.info("成功索引 {} 个文档", documents.size());
        }
    }

        /**
         * 将 Spring AI Document 转换为 ES cross-row
         */
        private CrossRowDocument convertToCrossRowDocument (Document document, List<Float> embedding){
            List<String> keywordList = Collections.emptyList();

            Object keywords = document.getMetadata().get("excerpt_keywords");
            if (keywords instanceof List<?> list) {  // Java 16+ 模式匹配
                keywordList = list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
            }

            return CrossRowDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .content(document.getText())
                    .embedding(embedding)
                    .keywords(keywordList)
                    .metadata(document.getMetadata())
                    .createdAt(Instant.now())
                    .build();
        }
}
