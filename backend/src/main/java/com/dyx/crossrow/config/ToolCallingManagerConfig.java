package com.dyx.crossrow.config;

import com.dyx.crossrow.tool.SimpleToolCallManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ToolCallingManagerConfig {

    @Bean
    @Primary
    public SimpleToolCallManager simpleToolCallManager(
            @Qualifier("defaultToolCallingManager") ToolCallingManager delegate,
            ToolCallback[] allTools) {
        return new SimpleToolCallManager(delegate, allTools);
    }

    @Bean("defaultToolCallingManager")
    public ToolCallingManager defaultToolCallingManager() {
        // Spring AI 提供的默认实现
        return ToolCallingManager.builder().build();
    }
}
