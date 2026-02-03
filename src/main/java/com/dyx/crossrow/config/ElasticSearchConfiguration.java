package com.dyx.crossrow.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.dyx.crossrow.properties.ElasticsearchProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticSearchConfiguration {
    @Bean
    public RestClient restClient(ElasticsearchProperties properties) {
        HttpHost httpHost = new HttpHost(
                properties.getHost(),
                properties.getPort(),
                properties.getProtocol()
        );

        // 建立客户端，创建底层 RestClient, APP layer
        RestClientBuilder builder = RestClient.builder(httpHost);

        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectionRequestTimeout(properties.getConnectionTimeout())
                .setSocketTimeout(properties.getSocketTimeout())
                .setConnectTimeout(properties.getConnectionTimeout())
        );
        return builder.build();
    }

    // 创建传输层 transport layer - JSON 序列化/反序列化 - 请求/响应的编解码
    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        // jackson json 处理器 - 传输
        ObjectMapper objectMapper = new ObjectMapper();
        // 处理时间类型
        objectMapper.registerModule(new JavaTimeModule());

        //json 传输
        return new RestClientTransport(
                restClient,
                new JacksonJsonpMapper(objectMapper)
        );
    }

    // 创造 elastic search 客户接口
    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    // elastic search 客户端初始化检查
    @Bean
    public ApplicationRunner elasticSearchClientTest(ElasticsearchClient elasticsearchClient){
        return args -> {
            try {
                boolean connected = elasticsearchClient.ping().value();
                if (connected) {
                    InfoResponse info = elasticsearchClient.info();
                    log.info("Elasticsearch 集群名称: {}, 版本号: {}",
                            info.clusterName(), info.version().number());
                }
            } catch (Exception e) {
                log.error("Elasticsearch 连接失败: {}", e.getMessage());
                // 根据需要决定是否抛出异常阻止启动
            }
        };

    }
}
