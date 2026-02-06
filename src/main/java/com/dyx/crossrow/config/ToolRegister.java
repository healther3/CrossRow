package com.dyx.crossrow.config;

import com.dyx.crossrow.tool.ImageGenerationTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegister {

    //集中注册工具
    @Bean
    public ToolCallback[] allTools(){
        ImageGenerationTool imageGenerationTool = new ImageGenerationTool();
        return ToolCallbacks.from(imageGenerationTool);
    }
}
