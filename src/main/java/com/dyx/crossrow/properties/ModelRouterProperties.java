package com.dyx.crossrow.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Model router configuration properties
 * For AI-based intelligent model routing
 */
@Data
@ConfigurationProperties(prefix = "app.model-router")
@Validated
public class ModelRouterProperties {

    /**
     * Enable intelligent routing
     */
    private boolean enabled = true;

    /**
     * Model for simple tasks (maps to "qwen" in router)
     */
    private String simpleModel = "qwen";

    /**
     * Model for complex tasks (maps to "gemini" in router)
     */
    private String complexModel = "gemini";

}
