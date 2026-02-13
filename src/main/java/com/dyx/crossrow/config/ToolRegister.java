package com.dyx.crossrow.config;

import com.dyx.crossrow.properties.SearchEngineProperties;
import com.dyx.crossrow.retriever.HybridDocumentRetriever;
import com.dyx.crossrow.service.ImageGenerationService;
import com.dyx.crossrow.tool.ImageGenerationTool;
import com.dyx.crossrow.tool.PhilosophyRetrieveTool;
import com.dyx.crossrow.tool.TerminateTool;
import com.dyx.crossrow.tool.WebSearchTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegister {

    //集中注册工具

    @Bean
    public WebSearchTool  webSearchTool(SearchEngineProperties searchEngineProperties){
        return new WebSearchTool(searchEngineProperties);
    }

    @Bean
    public ImageGenerationTool imageGenerationTool(ImageGenerationService imageGenerationService){
        return new ImageGenerationTool(imageGenerationService);
    }

    @Bean
    public TerminateTool terminateTool(){
        return new TerminateTool();
    }

    @Bean
    public PhilosophyRetrieveTool philosophyRetrieveTool(HybridDocumentRetriever hybridDocumentRetriever){
        return new PhilosophyRetrieveTool(hybridDocumentRetriever);
    }

    @Bean
    public ToolCallback[] allTools(WebSearchTool webSearchTool,
                                   ImageGenerationTool imageGenerationTool,
                                   TerminateTool terminateTool,
                                   PhilosophyRetrieveTool philosophyRetrieveTool){
        return ToolCallbacks.from(
                webSearchTool,
               imageGenerationTool,
                terminateTool,
                philosophyRetrieveTool
        );
    }
}
