package com.errorpurifier.service;

public enum LlmProvider {
    GEMINI("Gemini", "gemini-2.5-flash-lite"),
    OPENAI("OpenAI", "gpt-4.1-mini"),
    CLAUDE("Claude", "claude-sonnet-5");

    private final String displayName;
    private final String defaultModel;

    LlmProvider(String displayName, String defaultModel) {
        this.displayName = displayName;
        this.defaultModel = defaultModel;
    }

    public String displayName() {
        return displayName;
    }

    public String defaultModel() {
        return defaultModel;
    }
}
