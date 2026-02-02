package com.dyx.crossrow.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import com.dyx.crossrow.properties.ElasticsearchProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class ElasticsearchIndexManager {
    private final ElasticsearchClient esClient;
    private final ElasticsearchProperties properties;
    private static final int VECTOR_DIMENSIONS = 1024;

    public ElasticsearchIndexManager(ElasticsearchClient esClient, ElasticsearchProperties properties) {
        this.esClient = esClient;
        this.properties = properties;
    }

    /**
     * 检查索引是否存在
     */
    public boolean indexExists() throws IOException{
        // 调用 ES API: indices.exists()
        return esClient.indices().exists(builder -> builder.index(properties.getIndexName())).value();
    }

    /**
     * 创建索引（包含 Mapping 定义）
     */
    public void createIndex() throws IOException{
        String indexName = properties.getIndexName();

        // lambda expression 调用多个builder最后build
        CreateIndexRequest request = CreateIndexRequest.of(builder -> builder
                .index(indexName)
                // 主分片为1用于开发环境；单节点挂在docker，副本为1
                .settings(settings -> settings
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                )
                .mappings(mappings -> mappings
                        // id 查询
                        .properties("id", p -> p
                                .keyword(k -> k)
                        )
                        // 文本检索
                        .properties("content", p -> p
                                .text(t -> t
                                        .analyzer("ik_max_word")
                                )
                        )
                        // 向量检索
                        .properties("embedding", p -> p
                                .denseVector(dv -> dv
                                        .dims(VECTOR_DIMENSIONS)
                                        .index(true)
                                        .similarity("cosine")
                                )
                        )
                        // 元信息
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
     * 删除索引（开发/测试用）
     */
    public void deleteIndex() throws IOException{
        // 调用 ES API: indices.delete()
        esClient.indices().delete(builder -> builder.index(properties.getIndexName()));
    }

    /**
     * 确保索引存在（不存在则创建） 启动后立即执行
     */
    @PostConstruct
    public void ensureIndexExists() {
        try {
            if (!indexExists()) {
                log.info("正在初始化索引: {}...", properties.getIndexName());
                createIndex();
                log.info("索引 {} 创建成功", properties.getIndexName());
            } else {
                log.info("索引 {} 已存在，跳过创建步骤", properties.getIndexName());
            }
        } catch (IOException e) {
            log.error("检查/创建索引时发生网络异常: {}", e.getMessage());
            throw new RuntimeException("Elasticsearch 索引初始化失败，请检查服务状态", e);
        }
    }

}
