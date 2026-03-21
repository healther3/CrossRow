package com.dyx.crossrow.config;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.cloud.vertexai.VertexAI;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * Vertex AI 连接配置
 * 手动配置 VertexAiEmbeddingConnectionDetails 以支持自定义凭据文件
 */
@Configuration
public class VertexAiConfiguration {

    @Value("${spring.ai.vertex.ai.gemini.project-id}")
    private String projectId;

    @Value("${spring.ai.vertex.ai.gemini.location}")
    private String location;

    @Value("${gcp.credentials.location:classpath:config/gcp-key.json}")
    private Resource credentialsResource;

    private GoogleCredentials loadCredentials() throws IOException {
        if (credentialsResource.exists()) {
            return ServiceAccountCredentials.fromStream(credentialsResource.getInputStream())
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
        } else {
            return GoogleCredentials.getApplicationDefault()
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
        }
    }

    @Bean
    public VertexAI vertexAI() throws IOException {
        return new VertexAI.Builder()
                .setProjectId(projectId)
                .setCredentials(loadCredentials())
                .setLocation(location)
                .build();
    }
    @Bean
    public VertexAiEmbeddingConnectionDetails vertexAiEmbeddingConnectionDetails() throws IOException {
        GoogleCredentials credentials;
        
        if (credentialsResource.exists()) {
            // 从文件加载凭据
            credentials = ServiceAccountCredentials.fromStream(credentialsResource.getInputStream())
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
        } else {
            // 使用应用默认凭据 (ADC)
            credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
        }
        
        // 构建 API endpoint
        String endpoint = String.format("%s-aiplatform.googleapis.com:443", location);
        
        // 构建带凭据的 PredictionServiceSettings
        PredictionServiceSettings predictionServiceSettings = PredictionServiceSettings.newBuilder()
                .setEndpoint(endpoint)
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();
        
        return VertexAiEmbeddingConnectionDetails.builder()
                .projectId(projectId)
                .location(location)
                .apiEndpoint(endpoint)
                .predictionServiceSettings(predictionServiceSettings)
                .build();
    }
}
