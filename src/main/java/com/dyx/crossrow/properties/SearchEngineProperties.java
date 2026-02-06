package com.dyx.crossrow.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 自定义搜索引擎配置属性
 * Google Programmable Search Engine
 */
@Data
@ConfigurationProperties(prefix = "app.search-engine")
@Configuration
@Validated
public class SearchEngineProperties {
    private String apiKey;
}
