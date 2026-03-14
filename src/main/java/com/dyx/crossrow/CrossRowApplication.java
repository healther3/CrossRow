package com.dyx.crossrow;

import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
        OpenAiEmbeddingAutoConfiguration.class  // 禁用 OpenAI Embedding，只用 Vertex AI 的
})
public class CrossRowApplication {

    public static void main(String[] args) {
        System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", "./config/gcp-key.json");
        SpringApplication.run(CrossRowApplication.class, args);
    }

}
