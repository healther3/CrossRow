package com.dyx.crossrow.exceptions;

// 新建 exception/SessionAccessDeniedException.java
public class SessionAccessDeniedException extends RuntimeException {
    private final String userId;
    public SessionAccessDeniedException(String message, String userId) {
        super(message+userId);
        this.userId = userId;
    }
    public String getUserId() {
        return userId;
    }
}