package com.dyx.crossrow.advisor;
import com.dyx.crossrow.exceptions.UserAuthDeniedException;
import com.dyx.crossrow.repository.UserRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class SimpleAuthAdvisor implements BaseAdvisor {
    @Resource
    private UserRepository userRepository;

    private int order = Ordered.HIGHEST_PRECEDENCE;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 前置校验
        Long userId = (Long) request.context().get("userId");

        if (userId == null || userRepository.findById(userId).isEmpty()) {
            throw new UserAuthDeniedException(String.valueOf(userId));
        }
        // 可以修改 request 并返回
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // 后置处理（如记录日志、计费等）
        return response;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public SimpleAuthAdvisor withOrder(int order) {
        this.order = order;
        return this;
    }
}