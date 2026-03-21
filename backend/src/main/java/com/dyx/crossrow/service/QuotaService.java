package com.dyx.crossrow.service;

import com.dyx.crossrow.model.QuotaType;
import com.dyx.crossrow.model.Role;
import com.dyx.crossrow.model.User;
import com.dyx.crossrow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private static final String QUOTA_KEY_PREFIX = "quota:";

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * 获取用户某类型配额的限制
     */
    public int getQuotaLimit(String userId, QuotaType quotaType) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() == null) {
            return getDefaultLimit(quotaType);
        }

        Role role = user.getRole();
        return switch (quotaType) {
            case CHAT -> role.getDailyChatLimit();
            case AGENT -> role.getDailyAgentLimit();
        };
    }

    /**
     * 获取用户当前已使用的配额
     */
    public int getCurrentUsage(String userId, QuotaType quotaType) {
        String key = buildKey(userId, quotaType);
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0 : Integer.parseInt(value);
    }

    /**
     * 检查用户是否还有配额
     */
    public boolean hasQuota(String userId, QuotaType quotaType) {
        int limit = getQuotaLimit(userId, quotaType);
        if (limit == -1) {
            return true;  // -1 表示无限制
        }
        int usage = getCurrentUsage(userId, quotaType);
        return usage < limit;
    }

    /**
     * 消费一次配额
     */
    public void consumeQuota(String userId, QuotaType quotaType) {
        String key = buildKey(userId, quotaType);
        Long newValue = redisTemplate.opsForValue().increment(key);

        // 首次设置时添加过期时间（到当天结束）
        if (newValue != null && newValue == 1) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }

        log.debug("用户 {} 消费 {} 配额，当前使用: {}", userId, quotaType.getDisplayName(), newValue);
    }

    /**
     * 检查并消费配额（原子操作）
     * @return true 如果成功消费，false 如果配额不足
     */
    public boolean checkAndConsumeQuota(String userId, QuotaType quotaType) {
        int limit = getQuotaLimit(userId, quotaType);
        if (limit == -1) {
            return true;  // 无限制，不需要消费
        }

        String key = buildKey(userId, quotaType);
        Long currentUsage = redisTemplate.opsForValue().increment(key);

        if (currentUsage != null && currentUsage == 1) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }

        if (currentUsage != null && currentUsage > limit) {
            // 超额了，回滚
            redisTemplate.opsForValue().decrement(key);
            return false;
        }

        log.debug("用户 {} 消费 {} 配额，当前使用: {}/{}", userId, quotaType.getDisplayName(), currentUsage, limit);
        return true;
    }

    /**
     * 获取配额使用情况描述
     */
    public String getQuotaStatus(String userId, QuotaType quotaType) {
        int limit = getQuotaLimit(userId, quotaType);
        int usage = getCurrentUsage(userId, quotaType);

        if (limit == -1) {
            return quotaType.getDisplayName() + ": " + usage + "/无限制";
        }
        return quotaType.getDisplayName() + ": " + usage + "/" + limit;
    }

    private String buildKey(String userId, QuotaType quotaType) {
        return QUOTA_KEY_PREFIX + userId + ":" + quotaType.name() + ":" + LocalDate.now();
    }

    private int getDefaultLimit(QuotaType quotaType) {
        return switch (quotaType) {
            case CHAT -> 100;
            case AGENT -> 5;
        };
    }
}
