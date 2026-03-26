package com.dyx.crossrow.exceptions;

public class PromptInjectionDetectedException extends RuntimeException {

    private final String triggeredInput;

    public PromptInjectionDetectedException(String triggeredInput) {
        super("detect injection attack");
        this.triggeredInput = triggeredInput;
    }

    public String getTriggeredInput() {
        return triggeredInput;
    }
}
