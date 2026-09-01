package com.errorpurifier.ui;

import com.errorpurifier.ErrorPurifierBundle;
import com.errorpurifier.service.ApiService;
import com.errorpurifier.service.AnalysisMode;
import com.errorpurifier.service.ErrorPurifierSettings;
import com.errorpurifier.service.LlmClientService;
import com.errorpurifier.service.LlmProvider;
import com.errorpurifier.service.PrivacyConsentService;
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
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ErrorPurifierToolWindowFactory implements ToolWindowFactory {

    private static final Map<Project, ErrorPurifierPanel> PANELS = new ConcurrentHashMap<>();

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ErrorPurifierPanel panel = new ErrorPurifierPanel(project);
        PANELS.put(project, panel);
        Disposer.register(project, () -> PANELS.remove(project));

        Content content = ContentFactory.getInstance().createContent(panel.getMainPanel(), "", false);
        toolWindow.getContentManager().addContent(content);
    }

    public static ErrorPurifierPanel getPanel(Project project) {
        return PANELS.get(project);
    }

    public static class ErrorPurifierPanel {
        private final Project project;
        private final JPanel mainPanel = new JPanel(new BorderLayout());
        private final JLabel statusLabel = new JLabel(ErrorPurifierBundle.message("tool.status.ready"));
        private final JTextArea promptArea = new JTextArea();
        private final JTextArea refinedLogArea = new JTextArea();
        private final JTextArea usageArea = new JTextArea();
        private final JTabbedPane contentTabs = new JTabbedPane();
        private final JButton copyButton = new JButton(ErrorPurifierBundle.message("tool.button.copyPrompt"));
        private final JButton helpfulButton = new JButton(ErrorPurifierBundle.message("tool.button.helpful"));
        private final JButton unhelpfulButton = new JButton(ErrorPurifierBundle.message("tool.button.unhelpful"));
        private final JButton resolvedButton = new JButton(ErrorPurifierBundle.message("tool.button.resolved"));
        private final JButton appropriateRefinementButton = new JButton(ErrorPurifierBundle.message("tool.button.refinementAppropriate"));
        private final JButton missingContextButton = new JButton(ErrorPurifierBundle.message("tool.button.missingContext"));
        private final JButton tooNoisyButton = new JButton(ErrorPurifierBundle.message("tool.button.tooNoisy"));
        private final JButton usageButton = new JButton(ErrorPurifierBundle.message("tool.button.myUsage"));
        private final JComboBox<AnalysisMode> analysisModeBox = new JComboBox<>(AnalysisMode.values());
        private boolean answerCopyAvailable;
        private boolean refinedLogCopyAvailable;
        private boolean usageCopyAvailable;
        private String answerCopyLabel = ErrorPurifierBundle.message("tool.button.copyPrompt");
        private java.util.function.BiConsumer<Integer, Boolean> feedbackHandler;
        private java.util.function.Consumer<String> refinementFeedbackHandler;

        public ErrorPurifierPanel(Project project) {
            this.project = project;
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
            contentTabs.addTab(ErrorPurifierBundle.message("tool.tab.answer"), new JScrollPane(promptArea));
            contentTabs.addTab(ErrorPurifierBundle.message("tool.tab.preparedLog"), new JScrollPane(refinedLogArea));
            contentTabs.addTab(ErrorPurifierBundle.message("tool.tab.usage"), new JScrollPane(usageArea));
            contentTabs.addChangeListener(event -> updateCopyButton());
            mainPanel.add(contentTabs, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            actions.add(new JLabel(ErrorPurifierBundle.message("tool.label.analysisMode")));
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
            usageButton.addActionListener(event -> PrivacyConsentService.scheduleWithConsent(project, this::loadUsageSummary));
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
            statusLabel.setText(ErrorPurifierBundle.message(
                    selectedLog ? "tool.status.loadingSelected" : "tool.status.loadingFull"));
            statusLabel.setForeground(Color.DARK_GRAY);
            contentTabs.setSelectedIndex(0);
            promptArea.setText("");
            refinedLogArea.setText("");
            refinedLogCopyAvailable = false;
            setAnswerCopyAvailable(false, ErrorPurifierBundle.message("tool.button.copyPrompt"));
            setFeedbackButtonsEnabled(false);
            setRefinementFeedbackButtonsEnabled(false);
        }

        public void showPreparedPrompt(ApiService.PreparedPrompt response) {
            String cacheStatus = ErrorPurifierBundle.message(
                    response.cacheHit() ? "tool.status.cacheApplied" : "tool.status.standardApplied");
            statusLabel.setText(ErrorPurifierBundle.message("tool.status.prepared", cacheStatus,
                    formatNumber(response.originalCharacters()), formatNumber(response.preparedCharacters()),
                    refinementSummary(response.appliedRuleCounts(), response.protectedLineCount()),
                    repeatCompressionSummary(response.repeatedBlockCount(), response.omittedRepeatBlockCount(), response.repeatCompressionCharacters())));
            statusLabel.setForeground(response.cacheHit() ? new Color(40, 150, 40) : new Color(50, 90, 180));
            promptArea.setText(response.preparedPrompt());
            setRefinedLog(response.refinedLog());
            promptArea.setCaretPosition(0);
            contentTabs.setSelectedIndex(0);
            setAnswerCopyAvailable(true, ErrorPurifierBundle.message("tool.button.copyPrompt"));
        }

        public void setRefinedLog(String refinedLog) {
            refinedLogArea.setText(refinedLog == null || refinedLog.isBlank()
                    ? ErrorPurifierBundle.message("tool.preparedLog.empty") : refinedLog);
            refinedLogArea.setCaretPosition(0);
            refinedLogCopyAvailable = refinedLog != null && !refinedLog.isBlank();
            updateCopyButton();
        }

        public void startStreaming(LlmProvider provider, String model, AnalysisMode analysisMode) {
            analysisModeBox.setSelectedItem(analysisMode);
            statusLabel.setText(ErrorPurifierBundle.message("tool.status.streaming",
                    provider.displayName(), model, analysisMode.displayName()));
            statusLabel.setForeground(new Color(50, 90, 180));
            promptArea.setText("");
            contentTabs.setSelectedIndex(0);
            setAnswerCopyAvailable(false, ErrorPurifierBundle.message("tool.button.copyAnswer"));
            setFeedbackButtonsEnabled(false);
        }

        public void showInsufficientLog(String guidance) {
            statusLabel.setText(ErrorPurifierBundle.message("tool.status.moreLogNeeded"));
            statusLabel.setForeground(new Color(180, 120, 20));
            promptArea.setText(guidance);
            promptArea.setCaretPosition(0);
            contentTabs.setSelectedIndex(0);
            setAnswerCopyAvailable(true, ErrorPurifierBundle.message("tool.button.copyGuidance"));
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
            statusLabel.setText(ErrorPurifierBundle.message("tool.status.completed",
                    formatNumber(result.inputTokens()), formatNumber(result.outputTokens()),
                    logTruncated ? ErrorPurifierBundle.message("tool.status.logCompressed") : "",
                    refinementSummary(appliedRuleCounts, protectedLineCount),
                    repeatCompressionSummary(repeatedBlockCount, omittedRepeatBlockCount, repeatCompressionCharacters),
                    groundingWarnings.isEmpty() ? "" : ErrorPurifierBundle.message("tool.status.validationWarnings", groundingWarnings.size()),
                    formatNumber(evidenceLines.size()), formatLatency(result.latencyMs())));
            statusLabel.setForeground(groundingWarnings.isEmpty() ? new Color(40, 150, 40) : new Color(185, 100, 0));
            promptArea.setText(analysisSummary(provider, model, analysisMode, result, originalCharacters, refinedCharacters, preparedCharacters,
                    repeatedBlockCount, omittedRepeatBlockCount, repeatCompressionCharacters, diagnosticPlaybooks)
                    + groundingWarningSummary(groundingWarnings) + "\n\n" + promptArea.getText());
            appendEvidence(evidenceLines);
            promptArea.setCaretPosition(0);
            contentTabs.setSelectedIndex(0);
            setAnswerCopyAvailable(true, ErrorPurifierBundle.message("tool.button.copyAnswer"));
            this.feedbackHandler = feedbackHandler;
            this.refinementFeedbackHandler = refinementFeedbackHandler;
            setFeedbackButtonsEnabled(feedbackHandler != null);
            setRefinementFeedbackButtonsEnabled(true);
        }

        private void appendEvidence(Map<String, String> evidenceLines) {
            if (evidenceLines.isEmpty()) {
                return;
            }
            promptArea.append("\n\n---\n" + ErrorPurifierBundle.message("tool.section.evidence") + "\n");
            evidenceLines.forEach((lineNumber, text) -> promptArea.append(lineNumber + " | " + text + "\n"));
        }

        private String groundingWarningSummary(java.util.List<String> groundingWarnings) {
            if (groundingWarnings.isEmpty()) {
                return "";
            }
            return "\n\n" + ErrorPurifierBundle.message("tool.section.validationWarnings") + "\n" + groundingWarnings.stream()
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
            String estimatedTokenText = savedCharacters == 0
                    ? ErrorPurifierBundle.message("tool.summary.noEstimatedSavings")
                    : ErrorPurifierBundle.message("tool.summary.estimatedSavings", formatNumber(estimatedSavedTokens));
            String playbooks = diagnosticPlaybooks.isEmpty() ? ""
                    : ErrorPurifierBundle.message("tool.summary.playbooks", String.join(", ", diagnosticPlaybooks));
            return ErrorPurifierBundle.message("tool.summary.analysis", provider.displayName(), model,
                    analysisMode.displayName(), tokenUsageSummary(result), formatLatency(result.latencyMs()),
                    formatNumber(originalCharacters), formatNumber(refinedCharacters), formatDecimal(reductionRate),
                    repeatCompressionDetail(repeatedBlockCount, omittedRepeatBlockCount, repeatCompressionCharacters),
                    formatNumber(preparedCharacters), estimatedTokenText, playbooks);
        }

        private String formatNumber(long value) {
            return NumberFormat.getIntegerInstance(Locale.getDefault(Locale.Category.FORMAT)).format(value);
        }

        private String formatDecimal(double value) {
            NumberFormat format = NumberFormat.getNumberInstance(Locale.getDefault(Locale.Category.FORMAT));
            format.setMinimumFractionDigits(1);
            format.setMaximumFractionDigits(1);
            return format.format(value);
        }

        private String tokenUsageSummary(LlmClientService.LlmResult result) {
            long explainedTokens = (long) result.inputTokens() + result.outputTokens() + result.thinkingTokens();
            long otherTokens = Math.max(0, (long) result.totalTokens() - explainedTokens);
            String thinking = result.thinkingTokens() > 0
                    ? ErrorPurifierBundle.message("tool.summary.thinkingTokens", formatNumber(result.thinkingTokens())) : "";
            String other = otherTokens > 0
                    ? ErrorPurifierBundle.message("tool.summary.otherTokens", formatNumber(otherTokens)) : "";
            return ErrorPurifierBundle.message("tool.summary.tokens", formatNumber(result.inputTokens()),
                    formatNumber(result.outputTokens()), thinking, other, formatNumber(result.totalTokens()));
        }

        private String formatLatency(long latencyMs) {
            return latencyMs < 1_000
                    ? ErrorPurifierBundle.message("tool.latency.milliseconds", formatNumber(latencyMs))
                    : ErrorPurifierBundle.message("tool.latency.seconds", formatDecimal(latencyMs / 1_000.0));
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
                copyButton.setText(ErrorPurifierBundle.message("tool.button.copyPreparedLog"));
                return;
            }
            if (selectedTab == 2) {
                copyButton.setEnabled(usageCopyAvailable);
                copyButton.setText(ErrorPurifierBundle.message("tool.button.copyUsage"));
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
            String categories = appliedRuleCounts.isEmpty() ? ErrorPurifierBundle.message("tool.refinement.none") : appliedRuleCounts.entrySet().stream()
                    .map(entry -> entry.getKey() + " " + entry.getValue())
                    .limit(3)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(ErrorPurifierBundle.message("tool.refinement.none"));
            String protectedLines = protectedLineCount > 0
                    ? ErrorPurifierBundle.message("tool.refinement.protected", formatNumber(protectedLineCount)) : "";
            return ErrorPurifierBundle.message("tool.refinement.summary", categories, protectedLines);
        }

        private String repeatCompressionSummary(int repeatedBlockCount, int omittedRepeatBlockCount, int savedCharacters) {
            if (omittedRepeatBlockCount == 0) {
                return "";
            }
            return ErrorPurifierBundle.message("tool.repeat.summary", formatNumber(repeatedBlockCount),
                    formatNumber(repeatedBlockCount - omittedRepeatBlockCount), formatNumber(savedCharacters));
        }

        private String repeatCompressionDetail(int repeatedBlockCount, int omittedRepeatBlockCount, int savedCharacters) {
            if (omittedRepeatBlockCount == 0) {
                return "";
            }
            return ErrorPurifierBundle.message("tool.repeat.detail", formatNumber(repeatedBlockCount),
                    formatNumber(repeatedBlockCount - omittedRepeatBlockCount),
                    formatNumber(omittedRepeatBlockCount), formatNumber(savedCharacters));
        }

        public void showFeedbackSaved() {
            statusLabel.setText(ErrorPurifierBundle.message("tool.status.feedbackSaved"));
            statusLabel.setForeground(new Color(40, 150, 40));
        }

        public void showRefinementFeedbackSaved() {
            statusLabel.setText(ErrorPurifierBundle.message("tool.status.refinementFeedbackSaved"));
            statusLabel.setForeground(new Color(40, 150, 40));
        }

        public void showFeedbackError(String message) {
            statusLabel.setText(ErrorPurifierBundle.message("tool.status.feedbackFailed", message));
            statusLabel.setForeground(Color.RED);
        }

        public void showUsageReportError(String message) {
            statusLabel.setText(ErrorPurifierBundle.message("tool.status.usageReportFailed", message));
            statusLabel.setForeground(new Color(180, 120, 20));
        }

        private void loadUsageSummary() {
            usageButton.setEnabled(false);
            statusLabel.setText(ErrorPurifierBundle.message("tool.status.usageLoading"));
            statusLabel.setForeground(Color.DARK_GRAY);
            usageArea.setText(ErrorPurifierBundle.message("tool.status.usageLoading"));
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
            statusLabel.setText(ErrorPurifierBundle.message("tool.status.usageSummary",
                    formatNumber(usage.totalRequests()), formatNumber(usage.inputTokens()), formatNumber(usage.outputTokens())));
            statusLabel.setForeground(new Color(50, 90, 180));
            String change = usage.promptCharacterChangePercent() >= 0 ? "+" : "";
            usageArea.setText(ErrorPurifierBundle.message("tool.usage.summary",
                    formatNumber(usage.totalRequests()), formatNumber(usage.inputTokens()), formatNumber(usage.outputTokens()),
                    formatNumber(usage.thinkingTokens()), formatNumber(usage.totalTokens()), formatLatency(usage.averageLatencyMs()),
                    formatNumber(usage.helpfulResponses()), formatNumber(usage.unhelpfulResponses()), formatNumber(usage.resolvedResponses()),
                    formatNumber(usage.originalCharacters()), formatNumber(usage.preparedCharacters()), change,
                    formatDecimal(usage.promptCharacterChangePercent()), formatNumber(usage.repeatCompressionCharacters())));
            usageArea.setCaretPosition(0);
            contentTabs.setSelectedIndex(2);
            setUsageCopyAvailable(true);
        }

        private void showUsageError(String message) {
            usageButton.setEnabled(true);
            statusLabel.setText(ErrorPurifierBundle.message("tool.status.usageFailed", message));
            statusLabel.setForeground(Color.RED);
            usageArea.setText(ErrorPurifierBundle.message("tool.usage.failed", message));
            contentTabs.setSelectedIndex(2);
            setUsageCopyAvailable(true);
        }

        private void submitFeedback(int rating, boolean resolved) {
            if (feedbackHandler == null) return;
            PrivacyConsentService.scheduleWithConsent(project, () -> {
                setFeedbackButtonsEnabled(false);
                statusLabel.setText(ErrorPurifierBundle.message("tool.status.feedbackSaving"));
                feedbackHandler.accept(rating, resolved);
            });
        }

        private void submitRefinementFeedback(String feedbackType) {
            if (refinementFeedbackHandler == null) return;
            PrivacyConsentService.scheduleWithConsent(project, () -> {
                setRefinementFeedbackButtonsEnabled(false);
                statusLabel.setText(ErrorPurifierBundle.message("tool.status.refinementFeedbackSaving"));
                refinementFeedbackHandler.accept(feedbackType);
            });
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
