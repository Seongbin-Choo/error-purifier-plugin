package com.errorpurifier.service;

public enum AnalysisMode {
    FAST("빠른 분석", "최소 추론으로 핵심 원인과 우선 조치만 안내", "minimal", 0, 1_200),
    PRECISE("정밀 분석", "복합 원인과 확인 절차를 균형 있게 분석", "medium", 2_048, 2_400),
    DEEP("심층 분석", "연관 증상과 재현·수정 전략까지 깊게 분석", "high", 8_192, 4_096);

    private final String displayName;
    private final String description;
    private final String geminiThinkingLevel;
    private final int geminiThinkingBudget;
    private final int maxOutputTokens;

    AnalysisMode(String displayName, String description, String geminiThinkingLevel, int geminiThinkingBudget, int maxOutputTokens) {
        this.displayName = displayName;
        this.description = description;
        this.geminiThinkingLevel = geminiThinkingLevel;
        this.geminiThinkingBudget = geminiThinkingBudget;
        this.maxOutputTokens = maxOutputTokens;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
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
        return switch (this) {
            case FAST -> "핵심 원인 최대 3개와 바로 실행할 조치만 간결하게 답하세요. 로그 근거가 약한 가설은 길게 확장하지 마세요.";
            case PRECISE -> "근본 원인과 2차 증상을 구분하고, 확인 방법과 안전한 수정안을 균형 있게 설명하세요.";
            case DEEP -> "독립된 원인과 연쇄 증상을 빠짐없이 분류하고, 재현·검증 절차와 수정 우선순위를 상세히 설명하세요.";
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
