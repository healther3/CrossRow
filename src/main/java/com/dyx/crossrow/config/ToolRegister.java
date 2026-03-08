package com.dyx.crossrow.config;

import com.dyx.crossrow.elasticsearch.ElasticsearchDocumentStore;
import com.dyx.crossrow.properties.SearchEngineProperties;
import com.dyx.crossrow.retriever.HybridDocumentRetriever;
import com.dyx.crossrow.service.ImageGenerationService;
import com.dyx.crossrow.tool.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
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
    public PhilosophyRetrieveTool philosophyRetrieveTool(@Qualifier("philosophyRetriever") HybridDocumentRetriever retriever){
        return new PhilosophyRetrieveTool(retriever);
    }

    @Bean
    public PsychologyRetrieveTool psychologyRetrieveTool(@Qualifier("psychologyRetriever") HybridDocumentRetriever retriever){
        return new PsychologyRetrieveTool(retriever);
    }

    @Bean
    public SociologyRetrieveTool sociologyRetrieveTool(@Qualifier("sociologyRetriever") HybridDocumentRetriever retriever){
        return new SociologyRetrieveTool(retriever);
    }

    @Bean
    public UpdateUserMemoryTool updateUserMemoryTool(ElasticsearchDocumentStore elasticsearchDocumentStore){
        return new UpdateUserMemoryTool(elasticsearchDocumentStore);
    }

    @Bean
    public AskHumanTool askHumanTool(){
        return new AskHumanTool();
    }

    // 通用工具（所有 Agent 共享）
    @Bean
    public ToolCallback[] sharedTools(WebSearchTool webSearchTool,
                                      ImageGenerationTool imageGenerationTool,
                                      TerminateTool terminateTool,
                                      UpdateUserMemoryTool updateUserMemoryTool,
                                      AskHumanTool askHumanTool){
        return ToolCallbacks.from(
                webSearchTool,
                imageGenerationTool,
                terminateTool,
                updateUserMemoryTool,
                askHumanTool
        );
    }

    // Philosophy Agent 专用工具集
    @Bean
    public ToolCallback[] philosophyTools(ToolCallback[] sharedTools,
                                          PhilosophyRetrieveTool philosophyRetrieveTool){
        return mergeTools(sharedTools, ToolCallbacks.from(philosophyRetrieveTool));
    }

    // Psychology Agent 专用工具集
    @Bean
    public ToolCallback[] psychologyTools(ToolCallback[] sharedTools,
                                          PsychologyRetrieveTool psychologyRetrieveTool){
        return mergeTools(sharedTools, ToolCallbacks.from(psychologyRetrieveTool));
    }

    // Sociology Agent 专用工具集
    @Bean
    public ToolCallback[] sociologyTools(ToolCallback[] sharedTools,
                                         SociologyRetrieveTool sociologyRetrieveTool){
        return mergeTools(sharedTools, ToolCallbacks.from(sociologyRetrieveTool));
    }

    // 保留原有的 allTools 用于兼容 CrossRowAgent
    @Bean
    public ToolCallback[] allTools(ToolCallback[] sharedTools,
                                   PhilosophyRetrieveTool philosophyRetrieveTool){
        return mergeTools(sharedTools, ToolCallbacks.from(philosophyRetrieveTool));
    }

    private ToolCallback[] mergeTools(ToolCallback[] base, ToolCallback[] additional) {
        ToolCallback[] merged = new ToolCallback[base.length + additional.length];
        System.arraycopy(base, 0, merged, 0, base.length);
        System.arraycopy(additional, 0, merged, base.length, additional.length);
        return merged;
    }
}
