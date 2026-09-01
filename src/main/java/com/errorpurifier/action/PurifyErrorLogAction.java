package com.errorpurifier.action;

import com.errorpurifier.ErrorPurifierBundle;
import com.errorpurifier.service.ApiService;
import com.errorpurifier.service.ApiKeyStore;
import com.errorpurifier.service.ErrorPurifierSettings;
import com.errorpurifier.service.AnalysisMode;
import com.errorpurifier.service.AnswerGroundingValidator;
import com.errorpurifier.service.EvidenceLineExtractor;
import com.errorpurifier.service.LlmClientService;
import com.errorpurifier.service.LlmProvider;
import com.errorpurifier.service.PrivacyConsentService;
import com.errorpurifier.service.UserMessageFormatter;
import com.errorpurifier.ui.ErrorPurifierToolWindowFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class PurifyErrorLogAction extends AnAction {

    private final ApiService apiService = new ApiService();
    private final LlmClientService llmClientService = new LlmClientService();

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        Document document = editor.getDocument();
        String fullLog = document.getText();
        String selectedText = selectionModel.hasSelection() ? selectionModel.getSelectedText() : null;
        AnalysisInput input = selectAnalysisInput(fullLog, selectedText);
        String rawLog = input.rawLog();
        if (rawLog == null || rawLog.isBlank()) {
            Messages.showWarningDialog(project, ErrorPurifierBundle.message("action.warning.emptyLog"),
                    ErrorPurifierBundle.message("plugin.title"));
            return;
        }
        if (rawLog.length() > 100_000) {
            Messages.showWarningDialog(project, ErrorPurifierBundle.message("action.warning.logTooLong"),
                    ErrorPurifierBundle.message("plugin.title"));
            return;
        }
        if (!PrivacyConsentService.scheduleWithConsent(project, () -> startAnalysis(project, input))) {
            return;
        }
    }

    private void startAnalysis(Project project, AnalysisInput input) {
        String rawLog = input.rawLog();
        showToolWindow(project, panel -> panel.showLoading(input.selectedText() != null));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ApiService.PreparedPrompt response = apiService.preparePrompt(project, rawLog, input.selectedText());
                showToolWindow(project, panel -> panel.setRefinedLog(response.refinedLog()));
                if (!response.analysisReady()) {
                    showToolWindow(project, panel -> panel.showInsufficientLog(response.localizedGuidance()));
                    return;
                }
                ErrorPurifierSettings settings = ErrorPurifierSettings.getInstance();
                LlmProvider provider = settings.selectedProvider();
                String apiKey = ApiKeyStore.get(provider)
                        .filter(key -> !key.isBlank())
                        .orElseThrow(() -> new IllegalStateException(
                                ErrorPurifierBundle.message("action.error.apiKeyMissing", provider.displayName())));
                String model = settings.resolvedModel();
                AnalysisMode analysisMode = settings.selectedAnalysisMode();
                String analysisPrompt = response.preparedPrompt() + "\n\n"
                        + ErrorPurifierBundle.message("action.prompt.modeHeader", analysisMode.displayName()) + "\n"
                        + analysisMode.promptInstruction();

                showToolWindow(project, panel -> panel.startStreaming(provider, model, analysisMode));
                StringBuilder answer = new StringBuilder();
                LlmClientService.LlmResult result = llmClientService.stream(provider, model, apiKey, analysisPrompt, analysisMode, delta -> {
                    answer.append(delta);
                    updatePanel(project, panel -> panel.appendStreamingText(delta));
                });
                Map<String, String> evidenceLines = EvidenceLineExtractor.extract(response.preparedPrompt(), answer.toString());
                java.util.List<String> groundingWarnings = AnswerGroundingValidator.validate(response.refinedLog(), answer.toString(), evidenceLines);
                Long usageId = null;
                String usageError = null;
                try {
                    usageId = apiService.reportUsage(response, analysisPrompt, provider, model, result, java.util.List.copyOf(evidenceLines.keySet()), 0);
                } catch (Exception exception) {
                    usageError = UserMessageFormatter.backendFailure(exception);
                }
                Long finalUsageId = usageId;
                java.util.function.BiConsumer<Integer, Boolean> feedbackHandler = finalUsageId == null ? null :
                        (rating, resolved) -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
                            try {
                                apiService.reportFeedback(finalUsageId, rating, resolved);
                                showToolWindow(project, ErrorPurifierToolWindowFactory.ErrorPurifierPanel::showFeedbackSaved);
                            } catch (Exception feedbackError) {
                                showToolWindow(project, p -> p.showFeedbackError(UserMessageFormatter.backendFailure(feedbackError)));
                            }
                        });
                showToolWindow(project, panel -> panel.finishStreaming(provider, model, analysisMode, result,
                        response.originalCharacters(), response.refinedCharacters(), response.preparedCharacters(), evidenceLines, response.logTruncated(),
                        response.appliedRuleCounts(), response.protectedLineCount(), response.repeatedBlockCount(),
                        response.omittedRepeatBlockCount(), response.repeatCompressionCharacters(), response.diagnosticPlaybooks(), groundingWarnings, feedbackHandler,
                        feedbackType -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
                            try {
                                apiService.reportRefinementFeedback(response, feedbackType);
                                showToolWindow(project, ErrorPurifierToolWindowFactory.ErrorPurifierPanel::showRefinementFeedbackSaved);
                            } catch (Exception feedbackError) {
                                showToolWindow(project, p -> p.showFeedbackError(UserMessageFormatter.backendFailure(feedbackError)));
                            }
                        })
                ));
                if (usageError != null) {
                    String finalUsageError = usageError;
                    showToolWindow(project, panel -> panel.showUsageReportError(finalUsageError));
                }
            } catch (Exception exception) {
                ApplicationManager.getApplication().invokeLater(() ->
                        Messages.showErrorDialog(project,
                                ErrorPurifierBundle.message("action.error.analysisFailed",
                                        UserMessageFormatter.analysisFailure(exception)),
                                ErrorPurifierBundle.message("plugin.title"))
                );
            }
        });
    }

    static AnalysisInput selectAnalysisInput(String fullLog, String selectedText) {
        if (selectedText != null && !selectedText.isBlank()) {
            return new AnalysisInput(selectedText, selectedText);
        }
        return new AnalysisInput(fullLog, null);
    }

    record AnalysisInput(String rawLog, String selectedText) {
    }

    /**
     * 이미 열려 있는 패널만 갱신한다. 스트리밍처럼 호출이 잦은 경로에서
     * 토큰마다 툴윈도우를 다시 활성화해 포커스를 빼앗지 않도록 분리했다.
     */
    private void updatePanel(Project project, java.util.function.Consumer<ErrorPurifierToolWindowFactory.ErrorPurifierPanel> update) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ErrorPurifierToolWindowFactory.ErrorPurifierPanel panel = ErrorPurifierToolWindowFactory.getPanel(project);
            if (panel != null) {
                update.accept(panel);
            }
        });
    }

    private void showToolWindow(Project project, java.util.function.Consumer<ErrorPurifierToolWindowFactory.ErrorPurifierPanel> update) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("AI Error Purifier");
            if (toolWindow == null) {
                return;
            }
            toolWindow.show(() -> {
                ErrorPurifierToolWindowFactory.ErrorPurifierPanel panel = ErrorPurifierToolWindowFactory.getPanel(project);
                if (panel != null) {
                    update.accept(panel);
                }
            });
        });
    }
}
