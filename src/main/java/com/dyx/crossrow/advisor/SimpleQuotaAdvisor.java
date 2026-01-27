package com.dyx.crossrow.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleQuotaAdvisor implements BaseAdvisor {
    private final Map<String, AtomicInteger> dailyUsage = new ConcurrentHashMap<>();
    private final int dailyLimit;
    private int order = Ordered.HIGHEST_PRECEDENCE;

    public SimpleQuotaAdvisor(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String userId = (String) request.context().get("userId");

        if (userId == null) {
            throw new RuntimeException("未提供用户ID");
        }

        AtomicInteger usage = dailyUsage.computeIfAbsent(userId, k -> new AtomicInteger(0));

        if (usage.get() >= dailyLimit) {
            throw new RuntimeException("今日配额已用完: " + usage.get() + "/" + dailyLimit);
        }

        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // 调用成功后增加计数
        String userId = (String) response.context().get("userId");
        if (userId != null) {
            dailyUsage.get(userId).incrementAndGet();
        }
        return response;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    // 定时重置
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetDaily() {
        dailyUsage.clear();
    }
}