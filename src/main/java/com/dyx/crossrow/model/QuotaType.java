package com.dyx.crossrow.model;

public enum QuotaType {
    CHAT("对话"),
    AGENT("Agent");

    private final String displayName;

    QuotaType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
