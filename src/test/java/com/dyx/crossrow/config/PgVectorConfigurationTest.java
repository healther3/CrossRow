package com.dyx.crossrow.config;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class PgVectorConfigurationTest {
    @Resource
    VectorStore pgVectorStore;

    @Test
    void pgVectorStore() {

    }
}