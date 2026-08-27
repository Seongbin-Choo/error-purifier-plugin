package com.errorpurifier.ui;

import com.errorpurifier.service.ApiService;
import com.errorpurifier.service.AnalysisMode;
import com.errorpurifier.service.ErrorPurifierSettings;
import com.errorpurifier.service.LlmClientService;
import com.errorpurifier.service.LlmProvider;
import com.errorpurifier.service.UserMessageFormatter;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.datatransfer.StringSelection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ErrorPurifierToolWindowFactory implements ToolWindowFactory {

    private static final Map<Project, ErrorPurifierPanel> PANELS = new ConcurrentHashMap<>();

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ErrorPurifierPanel panel = new ErrorPurifierPanel();
        PANELS.put(project, panel);
        Disposer.register(project, () -> PANELS.remove(project));

        Content content = ContentFactory.getInstance().createContent(panel.getMainPanel(), "", false);
        toolWindow.getContentManager().addContent(content);
    }

    public static ErrorPurifierPanel getPanel(Project project) {
        return PANELS.get(project);
    }

    public static class ErrorPurifierPanel {
        private final JPanel mainPanel = new JPanel(new BorderLayout());
        private final JLabel statusLabel = new JLabel("콘솔 로그를 선택하거나 전체 로그를 정제하세요.");
        private final JTextArea promptArea = new JTextArea();
        private final JTextArea refinedLogArea = new JTextArea();
        private final JTextArea usageArea = new JTextArea();
        private final JTabbedPane contentTabs = new JTabbedPane();
        private final JButton copyButton = new JButton("프롬프트 복사");
        private final JButton helpfulButton = new JButton("👍 도움됨");
        private final JButton unhelpfulButton = new JButton("👎 도움 안 됨");
        private final JButton resolvedButton = new JButton("✅ 해결됨");
        private final JButton appropriateRefinementButton = new JButton("정제 적절");
        private final JButton missingContextButton = new JButton("핵심 누락");
        private final JButton tooNoisyButton = new JButton("노이즈 많음");
        private final JButton usageButton = new JButton("내 사용량");
        private final JComboBox<AnalysisMode> analysisModeBox = new JComboBox<>(AnalysisMode.values());
        private boolean answerCopyAvailable;
        private boolean refinedLogCopyAvailable;
        private boolean usageCopyAvailable;
        private String answerCopyLabel = "프롬프트 복사";
        private java.util.function.BiConsumer<Integer, Boolean> feedbackHandler;
        private java.util.function.Consumer<String> refinementFeedbackHandler;

        public ErrorPurifierPanel() {
            statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            mainPanel.add(statusLabel, BorderLayout.NORTH);

            promptArea.setEditable(false);
            promptArea.setLineWrap(true);
            promptArea.setWrapStyleWord(true);
            promptArea.setMargin(new Insets(10, 10, 10, 10));
            refinedLogArea.setEditable(false);
            refinedLogArea.setLineWrap(true);
            refinedLogArea.setWrapStyleWord(true);
            refinedLogArea.setMargin(new Insets(10, 10, 10, 10));
            usageArea.setEditable(false);
            usageArea.setLineWrap(true);
            usageArea.setWrapStyleWord(true);
            usageArea.setMargin(new Insets(10, 10, 10, 10));
            contentTabs.addTab("AI 답변", new JScrollPane(promptArea));
            contentTabs.addTab("정제 로그", new JScrollPane(refinedLogArea));
            contentTabs.addTab("내 사용량", new JScrollPane(usageArea));
            contentTabs.addChangeListener(event -> updateCopyButton());
            mainPanel.add(contentTabs, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            actions.add(new JLabel("분석 모드"));
            analysisModeBox.setSelectedItem(ErrorPurifierSettings.getInstance().selectedAnalysisMode());
            analysisModeBox.addActionListener(event -> {
                AnalysisMode mode = (AnalysisMode) analysisModeBox.getSelectedItem();
                if (mode != null) {
                    ErrorPurifierSettings.getInstance().analysisMode = mode.name();
                }
            });
            actions.add(analysisModeBox);
            copyButton.addActionListener(event -> CopyPasteManager.getInstance().setContents(new StringSelection(copyCurrentContent())));
            updateCopyButton();
            actions.add(copyButton);
            usageButton.addActionListener(event -> loadUsageSummary());
            actions.add(usageButton);
            helpfulButton.addActionListener(event -> submitFeedback(1, false));
            unhelpfulButton.addActionListener(event -> submitFeedback(-1, false));
            resolvedButton.addActionListener(event -> submitFeedback(1, true));
            setFeedbackButtonsEnabled(false);
            actions.add(helpfulButton);
            actions.add(unhelpfulButton);
            actions.add(resolvedButton);
            appropriateRefinementButton.addActionListener(event -> submitRefinementFeedback("APPROPRIATE"));
            missingContextButton.addActionListener(event -> submitRefinementFeedback("MISSING_CONTEXT"));
            tooNoisyButton.addActionListener(event -> submitRefinementFeedback("TOO_NOISY"));
            setRefinementFeedbackButtonsEnabled(false);
            actions.add(appropriateRefinementButton);
            actions.add(missingContextButton);
            actions.add(tooNoisyButton);
            mainPanel.add(actions, BorderLayout.SOUTH);
        }

        public JPanel getMainPanel() {
            return mainPanel;
        }

        public void showLoading(boolean selectedLog) {
            statusLabel.setText(selectedLog ? "선택한 로그를 정제하는 중…" : "전체 콘솔 로그를 정제하는 중…");
            statusLabel.setForeground(Color.DARK_GRAY);
            contentTabs.setSelectedIndex(0);
            promptArea.setText("");
            refinedLogArea.setText("");
            refinedLogCopyAvailable = false;
            setAnswerCopyAvailable(false, "프롬프트 복사");
            setFeedbackButtonsEnabled(false);
            setRefinementFeedbackButtonsEnabled(false);
        }

        public void showPreparedPrompt(ApiService.PreparedPrompt response) {
            String cacheStatus = response.cacheHit() ? "캐시 프로세스 적용" : "기본 정제 프로세스 적용";
            statusLabel.setText(cacheStatus + " · " + response.originalCharacters() + "자 → " + response.preparedCharacters()
                    + "자 · " + refinementSummary(response.appliedRuleCounts(), response.protectedLineCount())
                    + repeatCompressionSummary(response.repeatedBlockCount(), response.omittedRepeatBlockCount(), response.repeatCompressionCharacters()));
            statusLabel.setForeground(response.cacheHit() ? new Color(40, 150, 40) : new Color(50, 90, 180));
            promptArea.setText(response.preparedPrompt());
            setRefinedLog(response.refinedLog());
            promptArea.setCaretPosition(0);
            contentTabs.setSelectedIndex(0);
            setAnswerCopyAvailable(true, "프롬프트 복사");
        }

        public void setRefinedLog(String refinedLog) {
            refinedLogArea.setText(refinedLog == null || refinedLog.isBlank() ? "정제된 로그가 없습니다." : refinedLog);
            refinedLogArea.setCaretPosition(0);
            refinedLogCopyAvailable = refinedLog != null && !refinedLog.isBlank();
            updateCopyButton();
        }

        public void startStreaming(LlmProvider provider, String model, AnalysisMode analysisMode) {
            analysisModeBox.setSelectedItem(analysisMode);
            statusLabel.setText(provider.displayName() + " · " + model + " · " + analysisMode.displayName() + " 응답 생성 중…");
            statusLabel.setForeground(new Color(50, 90, 180));
            promptArea.setText("");
            contentTabs.setSelectedIndex(0);
            setAnswerCopyAvailable(false, "답변 복사");
            setFeedbackButtonsEnabled(false);
        }

        public void showInsufficientLog(String guidance) {
            statusLabel.setText("추가 로그가 필요합니다");
            statusLabel.setForeground(new Color(180, 120, 20));
            promptArea.setText(guidance);
            promptArea.setCaretPosition(0);
            contentTabs.setSelectedIndex(0);
            setAnswerCopyAvailable(true, "안내 복사");
        }

        public void appendStreamingText(String delta) {
            promptArea.append(delta);
            promptArea.setCaretPosition(promptArea.getDocument().getLength());
        }

        public void finishStreaming(LlmProvider provider, String model, AnalysisMode analysisMode, LlmClientService.LlmResult result,
                                    int originalCharacters, int refinedCharacters, int preparedCharacters,
                                    Map<String, String> evidenceLines, boolean logTruncated,
                                    Map<String, Integer> appliedRuleCounts, int protectedLineCount, int repeatedBlockCount,
                                    int omittedRepeatBlockCount, int repeatCompressionCharacters, java.util.List<String> diagnosticPlaybooks,
                                    java.util.List<String> groundingWarnings,
                                    java.util.function.BiConsumer<Integer, Boolean> feedbackHandler,
                                    java.util.function.Consumer<String> refinementFeedbackHandler) {
            statusLabel.setText("분석 완료 · 입력 " + result.inputTokens() + " 토큰 · 출력 " + result.outputTokens()
                    + " 토큰" + (logTruncated ? " · 로그 압축 적용" : "")
                    + " · " + refinementSummary(appliedRuleCounts, protectedLineCount)
                    + repeatCompressionSummary(repeatedBlockCount, omittedRepeatBlockCount, repeatCompressionCharacters)
                    + (groundingWarnings.isEmpty() ? "" : " · 검증 경고 " + groundingWarnings.size() + "건")
                    + " · 근거 " + evidenceLines.size() + "개 · " + result.latencyMs() + "ms");
            statusLabel.setForeground(groundingWarnings.isEmpty() ? new Color(40, 150, 40) : new Color(185, 100, 0));
            promptArea.setText(analysisSummary(provider, model, analysisMode, result, originalCharacters, refinedCharacters, preparedCharacters,
                    repeatedBlockCount, omittedRepeatBlockCount, repeatCompressionCharacters, diagnosticPlaybooks)
                    + groundingWarningSummary(groundingWarnings) + "\n\n" + promptArea.getText());
            appendEvidence(evidenceLines);
            promptArea.setCaretPosition(0);
            contentTabs.setSelectedIndex(0);
            setAnswerCopyAvailable(true, "답변 복사");
            this.feedbackHandler = feedbackHandler;
            this.refinementFeedbackHandler = refinementFeedbackHandler;
            setFeedbackButtonsEnabled(feedbackHandler != null);
            setRefinementFeedbackButtonsEnabled(true);
        }

        private void appendEvidence(Map<String, String> evidenceLines) {
            if (evidenceLines.isEmpty()) {
                return;
            }
            promptArea.append("\n\n---\n[AI 답변이 인용한 실제 로그]\n");
            evidenceLines.forEach((lineNumber, text) -> promptArea.append(lineNumber + " | " + text + "\n"));
        }

        private String groundingWarningSummary(java.util.List<String> groundingWarnings) {
            if (groundingWarnings.isEmpty()) {
                return "";
            }
            return "\n\n[답변 검증 경고]\n" + groundingWarnings.stream()
                    .map(warning -> "- " + warning)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        }

        private String analysisSummary(LlmProvider provider, String model, AnalysisMode analysisMode, LlmClientService.LlmResult result,
                                       int originalCharacters, int refinedCharacters, int preparedCharacters,
                                       int repeatedBlockCount, int omittedRepeatBlockCount, int repeatCompressionCharacters,
                                       java.util.List<String> diagnosticPlaybooks) {
            int savedCharacters = Math.max(0, originalCharacters - refinedCharacters);
            double reductionRate = originalCharacters == 0 ? 0 : savedCharacters * 100.0 / originalCharacters;
            int templateCharacters = Math.max(0, preparedCharacters - refinedCharacters);
            int comparableOriginalPromptCharacters = templateCharacters + originalCharacters;
            long estimatedSavedTokens = comparableOriginalPromptCharacters == 0 ? 0
                    : Math.round(result.inputTokens() * savedCharacters / (double) comparableOriginalPromptCharacters);
            String estimatedTokenText = savedCharacters == 0 ? "추정 입력 토큰 절감: 없음"
                    : "추정 입력 토큰 절감: 약 " + formatNumber(estimatedSavedTokens) + " 토큰";
            return "[분석 정보]\n"
                    + "모델: " + provider.displayName() + " · " + model + "\n"
                    + "분석 모드: " + analysisMode.displayName() + "\n"
                    + tokenUsageSummary(result) + "\n"
                    + "응답 시간: " + formatLatency(result.latencyMs()) + "\n"
                    + "로그 정제: " + formatNumber(originalCharacters) + "자 → " + formatNumber(refinedCharacters)
                    + "자 (" + String.format(Locale.KOREA, "%.1f", reductionRate) + "% 감소)\n"
                    + repeatCompressionDetail(repeatedBlockCount, omittedRepeatBlockCount, repeatCompressionCharacters)
                    + "최종 전송 프롬프트: " + formatNumber(preparedCharacters) + "자\n"
                    + estimatedTokenText + " (같은 프롬프트 구성·문자 비율 기준)"
                    + (diagnosticPlaybooks.isEmpty() ? "" : "\n적용 진단 가이드: " + String.join(", ", diagnosticPlaybooks));
        }

        private String formatNumber(long value) {
            return String.format(Locale.KOREA, "%,d", value);
        }

        private String tokenUsageSummary(LlmClientService.LlmResult result) {
            long explainedTokens = (long) result.inputTokens() + result.outputTokens() + result.thinkingTokens();
            long otherTokens = Math.max(0, (long) result.totalTokens() - explainedTokens);
            String summary = "실제 사용 토큰: 입력 " + formatNumber(result.inputTokens()) + " · 출력 " + formatNumber(result.outputTokens());
            if (result.thinkingTokens() > 0) {
                summary += " · 추론 " + formatNumber(result.thinkingTokens());
            }
            if (otherTokens > 0) {
                summary += " · 기타 " + formatNumber(otherTokens);
            }
            return summary + " · 총 " + formatNumber(result.totalTokens());
        }

        private String formatLatency(long latencyMs) {
            return latencyMs < 1_000 ? latencyMs + "ms" : String.format(Locale.KOREA, "%.1f초", latencyMs / 1_000.0);
        }

        private void setAnswerCopyAvailable(boolean available, String label) {
            answerCopyAvailable = available;
            answerCopyLabel = label;
            updateCopyButton();
        }

        private void setUsageCopyAvailable(boolean available) {
            usageCopyAvailable = available;
            updateCopyButton();
        }

        private void updateCopyButton() {
            int selectedTab = contentTabs.getSelectedIndex();
            if (selectedTab == 1) {
                copyButton.setEnabled(refinedLogCopyAvailable);
                copyButton.setText("정제 로그 복사");
                return;
            }
            if (selectedTab == 2) {
                copyButton.setEnabled(usageCopyAvailable);
                copyButton.setText("사용량 복사");
                return;
            }
            copyButton.setEnabled(answerCopyAvailable);
            copyButton.setText(answerCopyLabel);
        }

        private String copyCurrentContent() {
            return switch (contentTabs.getSelectedIndex()) {
                case 1 -> refinedLogArea.getText();
                case 2 -> usageArea.getText();
                default -> promptArea.getText();
            };
        }

        private String refinementSummary(Map<String, Integer> appliedRuleCounts, int protectedLineCount) {
            String categories = appliedRuleCounts.isEmpty() ? "노이즈 제거 없음" : appliedRuleCounts.entrySet().stream()
                    .map(entry -> entry.getKey() + " " + entry.getValue())
                    .limit(3)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("노이즈 제거 없음");
            return "정제 " + categories + (protectedLineCount > 0 ? " · 핵심 " + protectedLineCount + "줄 보존" : "");
        }

        private String repeatCompressionSummary(int repeatedBlockCount, int omittedRepeatBlockCount, int savedCharacters) {
            if (omittedRepeatBlockCount == 0) {
                return "";
            }
            return " · 반복 " + repeatedBlockCount + "회→" + (repeatedBlockCount - omittedRepeatBlockCount)
                    + "개 보존 · " + formatNumber(savedCharacters) + "자 절감";
        }

        private String repeatCompressionDetail(int repeatedBlockCount, int omittedRepeatBlockCount, int savedCharacters) {
            if (omittedRepeatBlockCount == 0) {
                return "";
            }
            return "반복 로그 압축: " + formatNumber(repeatedBlockCount) + "회 → "
                    + formatNumber(repeatedBlockCount - omittedRepeatBlockCount) + "개 보존"
                    + " (" + formatNumber(omittedRepeatBlockCount) + "개 생략 · "
                    + formatNumber(savedCharacters) + "자 절감)\n";
        }

        public void showFeedbackSaved() {
            statusLabel.setText("피드백이 저장되어 캐시 품질 개선에 반영됩니다.");
            statusLabel.setForeground(new Color(40, 150, 40));
        }

        public void showRefinementFeedbackSaved() {
            statusLabel.setText("정제 품질 피드백이 저장되었습니다. 원본 로그는 저장하지 않습니다.");
            statusLabel.setForeground(new Color(40, 150, 40));
        }

        public void showFeedbackError(String message) {
            statusLabel.setText("피드백 저장 실패: " + message);
            statusLabel.setForeground(Color.RED);
        }

        public void showUsageReportError(String message) {
            statusLabel.setText("분석 완료 · 사용량 저장 실패: " + message);
            statusLabel.setForeground(new Color(180, 120, 20));
        }

        private void loadUsageSummary() {
            usageButton.setEnabled(false);
            statusLabel.setText("내 사용량을 불러오는 중…");
            statusLabel.setForeground(Color.DARK_GRAY);
            usageArea.setText("내 사용량을 불러오는 중…");
            setUsageCopyAvailable(false);
            contentTabs.setSelectedIndex(2);
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    ApiService.UsageSummary usage = new ApiService().getUsageSummary();
                    ApplicationManager.getApplication().invokeLater(() -> showUsageSummary(usage));
                } catch (Exception exception) {
                    ApplicationManager.getApplication().invokeLater(() -> showUsageError(UserMessageFormatter.backendFailure(exception)));
                }
            });
        }

        private void showUsageSummary(ApiService.UsageSummary usage) {
            usageButton.setEnabled(true);
            statusLabel.setText("내 사용량 · 요청 " + usage.totalRequests() + "회 · 입력/출력 "
                    + usage.inputTokens() + "/" + usage.outputTokens() + " 토큰");
            statusLabel.setForeground(new Color(50, 90, 180));
            String change = usage.promptCharacterChangePercent() >= 0 ? "+" : "";
            usageArea.setText("[내 LLM 사용량]\n\n"
                    + "총 요청: " + usage.totalRequests() + "회\n"
                    + "실제 입력 토큰: " + usage.inputTokens() + "\n"
                    + "실제 출력 토큰: " + usage.outputTokens() + "\n"
                    + "실제 추론 토큰: " + usage.thinkingTokens() + "\n"
                    + "총 토큰: " + usage.totalTokens() + "\n"
                    + "평균 응답 시간: " + usage.averageLatencyMs() + "ms\n"
                    + "도움됨 / 도움 안 됨 / 해결됨: " + usage.helpfulResponses() + " / "
                    + usage.unhelpfulResponses() + " / " + usage.resolvedResponses() + "\n\n"
                    + "원본 로그 문자: " + usage.originalCharacters() + "\n"
                    + "LLM 전송 프롬프트 문자: " + usage.preparedCharacters() + " (" + change
                    + usage.promptCharacterChangePercent() + "%)\n"
                    + "반복 로그 압축 절감: " + usage.repeatCompressionCharacters() + "자\n\n"
                    + "토큰 수는 각 LLM API가 반환한 실제 사용량입니다. 문자 변화율은 정제 로그와 안내 문구를 포함한 최종 프롬프트의 크기 비교이며, 토큰 절감량 자체를 뜻하지는 않습니다.\n"
                    + "API 키, 원본 로그, AI 답변은 이 화면이나 서버 사용량 통계에 저장하지 않습니다.");
            usageArea.setCaretPosition(0);
            contentTabs.setSelectedIndex(2);
            setUsageCopyAvailable(true);
        }

        private void showUsageError(String message) {
            usageButton.setEnabled(true);
            statusLabel.setText("사용량 조회 실패: " + message);
            statusLabel.setForeground(Color.RED);
            usageArea.setText("사용량을 불러오지 못했습니다.\n\n" + message);
            contentTabs.setSelectedIndex(2);
            setUsageCopyAvailable(true);
        }

        private void submitFeedback(int rating, boolean resolved) {
            if (feedbackHandler == null) return;
            setFeedbackButtonsEnabled(false);
            statusLabel.setText("피드백 저장 중…");
            feedbackHandler.accept(rating, resolved);
        }

        private void submitRefinementFeedback(String feedbackType) {
            if (refinementFeedbackHandler == null) return;
            setRefinementFeedbackButtonsEnabled(false);
            statusLabel.setText("정제 품질 피드백 저장 중…");
            refinementFeedbackHandler.accept(feedbackType);
        }

        private void setFeedbackButtonsEnabled(boolean enabled) {
            helpfulButton.setEnabled(enabled);
            unhelpfulButton.setEnabled(enabled);
            resolvedButton.setEnabled(enabled);
        }

        private void setRefinementFeedbackButtonsEnabled(boolean enabled) {
            appropriateRefinementButton.setEnabled(enabled);
            missingContextButton.setEnabled(enabled);
            tooNoisyButton.setEnabled(enabled);
        }
    }
}
