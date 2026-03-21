package com.dyx.crossrow.exceptions;

import com.dyx.crossrow.model.QuotaType;

public class QuotaExceededException extends RuntimeException {
    private final String userId;
    private final QuotaType quotaType;
    private final int limit;
    private final int usage;

    public QuotaExceededException(String userId, QuotaType quotaType, int limit, int usage) {
        super(String.format("用户 %s 的%s配额已用完: %d/%d", userId, quotaType.getDisplayName(), usage, limit));
        this.userId = userId;
        this.quotaType = quotaType;
        this.limit = limit;
        this.usage = usage;
    }

    public String getUserId() {
        return userId;
    }

    public QuotaType getQuotaType() {
        return quotaType;
    }

    public int getLimit() {
        return limit;
    }

    public int getUsage() {
        return usage;
    }
}
