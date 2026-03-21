package com.dyx.crossrow.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ElasticSearchConfigurationTest {
    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Test
    void testConnection() throws IOException {
        InfoResponse info = elasticsearchClient.info();

        Assertions.assertTrue(elasticsearchClient.ping().value());
    }
}