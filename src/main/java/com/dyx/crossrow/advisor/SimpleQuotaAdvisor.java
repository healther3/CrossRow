package com.dyx.crossrow.advisor;

import com.dyx.crossrow.exceptions.QuotaExceededException;
import com.dyx.crossrow.model.QuotaType;
import com.dyx.crossrow.service.QuotaService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.core.Ordered;

public class SimpleQuotaAdvisor implements BaseAdvisor {
    private final QuotaService quotaService;
    private final QuotaType quotaType;
    private final int order;

    public SimpleQuotaAdvisor(QuotaService quotaService, QuotaType quotaType) {
        this(quotaService, quotaType, Ordered.HIGHEST_PRECEDENCE);
    }

    public SimpleQuotaAdvisor(QuotaService quotaService, QuotaType quotaType, int order) {
        this.quotaService = quotaService;
        this.quotaType = quotaType;
        this.order = order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String userId = (String) request.context().get("userId");

        if (userId == null) {
            throw new RuntimeException("未提供用户ID");
        }

        if (!quotaService.hasQuota(userId, quotaType)) {
            int limit = quotaService.getQuotaLimit(userId, quotaType);
            int usage = quotaService.getCurrentUsage(userId, quotaType);
            throw new QuotaExceededException(userId, quotaType, limit, usage);
        }

        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        String userId = (String) response.context().get("userId");
        if (userId != null) {
            quotaService.consumeQuota(userId, quotaType);
        }
        return response;
    }

    @Override
    public int getOrder() {
        return this.order;
    }
}