package com.dyx.crossrow.config;

import com.dyx.crossrow.properties.SearchEngineProperties;
import com.dyx.crossrow.service.ImageGenerationService;
import com.dyx.crossrow.tool.ImageGenerationTool;
import com.dyx.crossrow.tool.WebSearchTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ToolRegister {

    //集中注册工具
    @Bean
    public ToolCallback[] allSearchTools(){
        return ToolCallbacks.from();
    }

    @Bean
    public WebSearchTool  webSearchTool(SearchEngineProperties searchEngineProperties){
        return new WebSearchTool(searchEngineProperties);
    }

    @Bean
    public ImageGenerationTool imageGenerationTool(ImageGenerationService imageGenerationService){
        return new ImageGenerationTool(imageGenerationService);
    }
}
