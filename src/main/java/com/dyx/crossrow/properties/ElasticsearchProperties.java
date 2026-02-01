package com.dyx.crossrow.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 设计清单：
 * 1. 使用 @ConfigurationProperties 绑定 yaml 配置
 * 2. 提供合理的默认值
 * 3. 添加 JSR-303 校验注解
 * 4. 使用 Lombok 简化代码
 */
@Data
@ConfigurationProperties(prefix = "spring.elasticsearch")
@Validated
public class ElasticsearchProperties {

    // connection settings
    private String host = "localhost";
    private int port = 9200;
    private String protocol = "http";

    private int connectionTimeout = 5000;
    private int socketTimeout = 60000;

    private String indexName = "philosophy_docs";
}
