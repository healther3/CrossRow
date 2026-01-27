package com.dyx.crossrow.config;

import com.dyx.crossrow.chatmemory.RedisChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class ChatMemoryConfiguration {

    @Bean
    public ChatMemory chatMemory(StringRedisTemplate redisTemplate) {
        // 设置对话记录保留30天
        return new RedisChatMemory(redisTemplate, Duration.ofDays(30));
    }
}