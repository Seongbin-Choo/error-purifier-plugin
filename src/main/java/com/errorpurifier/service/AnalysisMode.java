package com.errorpurifier.service;

import com.errorpurifier.ErrorPurifierBundle;

public enum AnalysisMode {
    FAST("fast", "minimal", 0, 1_200),
    PRECISE("precise", "medium", 2_048, 2_400),
    DEEP("deep", "high", 8_192, 4_096);

    private final String bundleKeySegment;
    private final String geminiThinkingLevel;
    private final int geminiThinkingBudget;
    private final int maxOutputTokens;

    AnalysisMode(String bundleKeySegment, String geminiThinkingLevel, int geminiThinkingBudget, int maxOutputTokens) {
        this.bundleKeySegment = bundleKeySegment;
        this.geminiThinkingLevel = geminiThinkingLevel;
        this.geminiThinkingBudget = geminiThinkingBudget;
        this.maxOutputTokens = maxOutputTokens;
    }

    public String displayName() {
        return ErrorPurifierBundle.message("analysis.mode." + bundleKeySegment + ".name");
    }

    public String description() {
        return ErrorPurifierBundle.message("analysis.mode." + bundleKeySegment + ".description");
    }

    public String geminiThinkingLevel() {
        return geminiThinkingLevel;
    }

    public int geminiThinkingBudget() {
        return geminiThinkingBudget;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public String promptInstruction() {
        return ErrorPurifierBundle.message("analysis.mode." + bundleKeySegment + ".prompt");
    }

    @Override
    public String toString() {
        return displayName();
    }
}
