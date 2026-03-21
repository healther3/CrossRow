package com.dyx.crossrow.exceptions;

public class UserAuthDeniedException extends RuntimeException {

    private final String userId;
    public UserAuthDeniedException(String userId) {
        super("无权访问 AI 服务: " + userId);
        this.userId = userId;
    }
    public String getUserId() {
        return userId;
    }
}
