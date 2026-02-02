package com.dyx.crossrow.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class ElasticsearchIndexManagerTest {
    @Autowired
    private ElasticsearchIndexManager indexManager;

    @Autowired
    private ElasticsearchClient client;

    @Test
    void testIndexInitialization() throws IOException {
        // 1. 验证索引确实存在
        boolean exists = indexManager.indexExists();
        Assertions.assertTrue(exists);

        // 2. 深度验证：检查 Mapping 结构 (这是体现深度的点)
        // 看看 embedding 字段是不是真的是 dense_vector，维度对不对
        GetIndexResponse getIndexResponse = client.indices().get(g -> g.index("philosophy_docs"));
        var mapping = getIndexResponse.get("philosophy_docs").mappings().properties();

        Assertions.assertTrue(mapping.containsKey("embedding"));
        log.info("索引结构验证完成，字段包含: {}", mapping.keySet());
    }
}