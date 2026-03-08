package com.dyx.crossrow.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import com.dyx.crossrow.properties.ElasticsearchProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class ElasticsearchIndexManager {
    private final ElasticsearchClient esClient;
    private final ElasticsearchProperties properties;
    private static final int VECTOR_DIMENSIONS = 768;
    
    private static final List<String> DOMAIN_INDICES = List.of(
            "philosophy_docs",
            "psychology_docs", 
            "sociology_docs"
    );

    public ElasticsearchIndexManager(ElasticsearchClient esClient, ElasticsearchProperties properties) {
        this.esClient = esClient;
        this.properties = properties;
    }

    /**
     * 检查指定索引是否存在
     */
    public boolean indexExists(String indexName) throws IOException {
        return esClient.indices().exists(builder -> builder.index(indexName)).value();
    }

    /**
     * 检查默认索引是否存在
     */
    public boolean indexExists() throws IOException {
        return indexExists(properties.getIndexName());
    }

    /**
     * 创建指定名称的索引（包含 Mapping 定义）
     */
    public void createIndex(String indexName) throws IOException {
        CreateIndexRequest request = CreateIndexRequest.of(builder -> builder
                .index(indexName)
                .settings(settings -> settings
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                )
                .mappings(mappings -> mappings
                        .properties("id", p -> p
                                .keyword(k -> k)
                        )
                        .properties("content", p -> p
                                .text(t -> t
                                        .analyzer("ik_max_word")
                                )
                        )
                        .properties("embedding", p -> p
                                .denseVector(dv -> dv
                                        .dims(VECTOR_DIMENSIONS)
                                        .index(true)
                                        .similarity("cosine")
                                )
                        )
                        .properties("metadata", p -> p
                                .object(o -> o
                                        .properties("filename", mp -> mp.keyword(k -> k))
                                        .properties("type", mp -> mp.keyword(k -> k))
                                )
                        ).properties("created_at", p -> p
                                .date(d -> d)
                        )
                )
        );

        CreateIndexResponse response = esClient.indices().create(request);

        if (!response.acknowledged()) {
            throw new RuntimeException("索引创建失败: " + indexName);
        }
    }

    /**
     * 创建默认索引
     */
    public void createIndex() throws IOException {
        createIndex(properties.getIndexName());
    }

    /**
     * 删除指定索引（开发/测试用）
     */
    public void deleteIndex(String indexName) throws IOException {
        esClient.indices().delete(builder -> builder.index(indexName));
    }

    /**
     * 删除默认索引
     */
    public void deleteIndex() throws IOException {
        deleteIndex(properties.getIndexName());
    }

    /**
     * 确保指定索引存在
     */
    public void ensureIndexExists(String indexName) {
        try {
            if (!indexExists(indexName)) {
                log.info("正在初始化索引: {}...", indexName);
                createIndex(indexName);
                log.info("索引 {} 创建成功", indexName);
            } else {
                log.info("索引 {} 已存在，跳过创建步骤", indexName);
            }
        } catch (IOException e) {
            log.error("检查/创建索引 {} 时发生网络异常: {}", indexName, e.getMessage());
            throw new RuntimeException("Elasticsearch 索引初始化失败，请检查服务状态", e);
        }
    }

    /**
     * 确保所有领域索引都存在 - 启动后立即执行
     */
    @PostConstruct
    public void ensureAllIndicesExist() {
        log.info("开始初始化所有领域索引...");
        for (String indexName : DOMAIN_INDICES) {
            ensureIndexExists(indexName);
        }
        log.info("所有领域索引初始化完成");
    }

    /**
     * 确保默认索引存在
     */
    public void ensureIndexExists() {
        ensureIndexExists(properties.getIndexName());
    }

}
