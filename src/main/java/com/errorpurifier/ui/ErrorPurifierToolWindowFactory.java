package com.errorpurifier.ui;

import com.errorpurifier.service.ApiService;
import com.errorpurifier.service.LlmClientService;
import com.errorpurifier.service.LlmProvider;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.datatransfer.StringSelection;
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
        private final JButton copyButton = new JButton("프롬프트 복사");
        private final JButton helpfulButton = new JButton("👍 도움됨");
        private final JButton unhelpfulButton = new JButton("👎 도움 안 됨");
        private final JButton resolvedButton = new JButton("✅ 해결됨");
        private final JButton appropriateRefinementButton = new JButton("정제 적절");
        private final JButton missingContextButton = new JButton("핵심 누락");
        private final JButton tooNoisyButton = new JButton("노이즈 많음");
        private final JButton usageButton = new JButton("내 사용량");
        private java.util.function.BiConsumer<Integer, Boolean> feedbackHandler;
        private java.util.function.Consumer<String> refinementFeedbackHandler;

        public ErrorPurifierPanel() {
            statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            mainPanel.add(statusLabel, BorderLayout.NORTH);

            promptArea.setEditable(false);
            promptArea.setLineWrap(true);
            promptArea.setWrapStyleWord(true);
            promptArea.setMargin(new Insets(10, 10, 10, 10));
            mainPanel.add(new JScrollPane(promptArea), BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            copyButton.setEnabled(false);
            copyButton.addActionListener(event -> CopyPasteManager.getInstance().setContents(new StringSelection(promptArea.getText())));
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
            promptArea.setText("");
            copyButton.setEnabled(false);
            copyButton.setText("프롬프트 복사");
            setFeedbackButtonsEnabled(false);
            setRefinementFeedbackButtonsEnabled(false);
        }

        public void showPreparedPrompt(ApiService.PreparedPrompt response) {
            String cacheStatus = response.cacheHit() ? "캐시 프로세스 적용" : "기본 정제 프로세스 적용";
            statusLabel.setText(cacheStatus + " · " + response.originalCharacters() + "자 → " + response.preparedCharacters()
                    + "자 · " + refinementSummary(response.appliedRuleCounts(), response.protectedLineCount()));
            statusLabel.setForeground(response.cacheHit() ? new Color(40, 150, 40) : new Color(50, 90, 180));
            promptArea.setText(response.preparedPrompt());
            promptArea.setCaretPosition(0);
            copyButton.setEnabled(true);
            copyButton.setText("프롬프트 복사");
        }

        public void startStreaming(LlmProvider provider, String model) {
            statusLabel.setText(provider.displayName() + " · " + model + " 응답 생성 중…");
            statusLabel.setForeground(new Color(50, 90, 180));
            promptArea.setText("");
            copyButton.setEnabled(false);
            setFeedbackButtonsEnabled(false);
        }

        public void showInsufficientLog(String guidance) {
            statusLabel.setText("추가 로그가 필요합니다");
            statusLabel.setForeground(new Color(180, 120, 20));
            promptArea.setText(guidance);
            promptArea.setCaretPosition(0);
            copyButton.setEnabled(true);
            copyButton.setText("안내 복사");
        }

        public void appendStreamingText(String delta) {
            promptArea.append(delta);
            promptArea.setCaretPosition(promptArea.getDocument().getLength());
        }

        public void finishStreaming(LlmClientService.LlmResult result, Map<String, String> evidenceLines, boolean logTruncated,
                                    Map<String, Integer> appliedRuleCounts, int protectedLineCount,
                                    java.util.function.BiConsumer<Integer, Boolean> feedbackHandler,
                                    java.util.function.Consumer<String> refinementFeedbackHandler) {
            statusLabel.setText("분석 완료 · 입력 " + result.inputTokens() + " 토큰 · 출력 " + result.outputTokens()
                    + " 토큰" + (logTruncated ? " · 로그 압축 적용" : "")
                    + " · " + refinementSummary(appliedRuleCounts, protectedLineCount)
                    + " · 근거 " + evidenceLines.size() + "개 · " + result.latencyMs() + "ms");
            statusLabel.setForeground(new Color(40, 150, 40));
            appendEvidence(evidenceLines);
            copyButton.setEnabled(true);
            copyButton.setText("답변 복사");
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

        private String refinementSummary(Map<String, Integer> appliedRuleCounts, int protectedLineCount) {
            String categories = appliedRuleCounts.isEmpty() ? "노이즈 제거 없음" : appliedRuleCounts.entrySet().stream()
                    .map(entry -> entry.getKey() + " " + entry.getValue())
                    .limit(3)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("노이즈 제거 없음");
            return "정제 " + categories + (protectedLineCount > 0 ? " · 핵심 " + protectedLineCount + "줄 보존" : "");
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
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    ApiService.UsageSummary usage = new ApiService().getUsageSummary();
                    ApplicationManager.getApplication().invokeLater(() -> showUsageSummary(usage));
                } catch (Exception exception) {
                    ApplicationManager.getApplication().invokeLater(() -> showUsageError(exception.getMessage()));
                }
            });
        }

        private void showUsageSummary(ApiService.UsageSummary usage) {
            usageButton.setEnabled(true);
            statusLabel.setText("내 사용량 · 요청 " + usage.totalRequests() + "회 · 입력/출력 "
                    + usage.inputTokens() + "/" + usage.outputTokens() + " 토큰");
            statusLabel.setForeground(new Color(50, 90, 180));
            String change = usage.promptCharacterChangePercent() >= 0 ? "+" : "";
            promptArea.setText("[내 LLM 사용량]\n\n"
                    + "총 요청: " + usage.totalRequests() + "회\n"
                    + "실제 입력 토큰: " + usage.inputTokens() + "\n"
                    + "실제 출력 토큰: " + usage.outputTokens() + "\n"
                    + "총 토큰: " + usage.totalTokens() + "\n"
                    + "평균 응답 시간: " + usage.averageLatencyMs() + "ms\n"
                    + "도움됨 / 도움 안 됨 / 해결됨: " + usage.helpfulResponses() + " / "
                    + usage.unhelpfulResponses() + " / " + usage.resolvedResponses() + "\n\n"
                    + "원본 로그 문자: " + usage.originalCharacters() + "\n"
                    + "LLM 전송 프롬프트 문자: " + usage.preparedCharacters() + " (" + change
                    + usage.promptCharacterChangePercent() + "%)\n\n"
                    + "토큰 수는 각 LLM API가 반환한 실제 사용량입니다. 문자 변화율은 정제 로그와 안내 문구를 포함한 최종 프롬프트의 크기 비교이며, 토큰 절감량 자체를 뜻하지는 않습니다.\n"
                    + "API 키, 원본 로그, AI 답변은 이 화면이나 서버 사용량 통계에 저장하지 않습니다.");
            promptArea.setCaretPosition(0);
            copyButton.setEnabled(true);
        }

        private void showUsageError(String message) {
            usageButton.setEnabled(true);
            statusLabel.setText("사용량 조회 실패: " + message);
            statusLabel.setForeground(Color.RED);
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
