package com.dyx.crossrow.config;

import com.dyx.crossrow.properties.ImageModelProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.google.genai.Client;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * Google GenAI SDK 配置
 * 使用与 VertexAiConfiguration 相同的方式加载凭证
 */
@Configuration
@EnableConfigurationProperties(ImageModelProperties.class)
public class GenAiConfiguration {

    @Value("${gcp.credentials.location:classpath:config/gcp-key.json}")
    private Resource credentialsResource;

    @Bean
    public Client genAiClient(ImageModelProperties properties) throws IOException {
        GoogleCredentials credentials;

        if (credentialsResource.exists()) {
            // 从文件加载凭据（与 VertexAiConfiguration 保持一致）
            credentials = ServiceAccountCredentials.fromStream(credentialsResource.getInputStream())
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
        } else {
            // 使用应用默认凭据 (ADC)
            credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
        }

        return Client.builder()
                .vertexAI(true)
                .project(properties.getProjectId())
                .location(properties.getLocation())
                .credentials(credentials)
                .build();
    }
}
