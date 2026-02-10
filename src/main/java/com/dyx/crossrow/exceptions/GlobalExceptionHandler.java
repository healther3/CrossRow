package com.dyx.crossrow.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserAuthDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(UserAuthDeniedException e) {
        log.warn("User Auth Fail: {}", e.getMessage());  // 只记录简单日志

        Map<String, Object> response = Map.of(
                "success", false,
                "error", e.getMessage(),
                "userId", e.getUserId() != null ? e.getUserId() : "unknown"
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AgentStateException.class)
    public ResponseEntity<Map<String, Object>> handleAgentStateException(AgentStateException e) {
        log.warn("Agent State Exception: {}", e.getMessage());  // 只记录简单日志

        Map<String, Object> response = Map.of(
                "success", false,
                "error", e.getMessage(),
                "agentState", e.getAgentState().name()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(EmptyUserPromptException.class)
    public ResponseEntity<Map<String, Object>> handleEmptyUserPromptException(EmptyUserPromptException e) {
        log.warn("Empty User Prompt: {}", e.getMessage());  // 只记录简单日志

        Map<String, Object> response = Map.of(
                "success", false,
                "error", e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);

    }
}