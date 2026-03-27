package com.dyx.crossrow.service;

import com.dyx.crossrow.model.User;
import com.dyx.crossrow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Get user's preferred model name.
     * Returns "gemini" as default if user not found or preference not set.
     */
    public String getPreferredModel(String userId) {
        if (userId == null || userId.isBlank()) {
            return "gemini";
        }
        return userRepository.findById(userId)
                .map(user -> {
                    String model = user.getPreferredModel();
                    return (model == null || model.isBlank()) ? "gemini" : model;
                })
                .orElse("gemini");
    }

    @Transactional
    public void setPreferredModel(String userId, String modelName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setPreferredModel(modelName);
        userRepository.save(user);
        log.info("Updated model preference for user {}: {}", userId, modelName);
    }

    public User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}
