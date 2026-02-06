package com.dyx.crossrow.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 自定义搜索引擎配置属性
 * Google Programmable Search Engine
 */
@Data
@ConfigurationProperties(prefix = "app.search-engine")
@Validated
public class SearchEngineProperties {
    private String apiKey;
    private String searchEngineId;
}
