package com.errorpurifier.action;

import com.errorpurifier.service.ApiService;
import com.errorpurifier.service.ApiKeyStore;
import com.errorpurifier.service.ErrorPurifierSettings;
import com.errorpurifier.service.LlmClientService;
import com.errorpurifier.service.LlmProvider;
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

import java.util.LinkedHashMap;
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
        String rawLog = fullLog.length() <= 100_000 ? fullLog
                : (selectedText != null && !selectedText.isBlank() ? selectedText : fullLog);
        if (rawLog == null || rawLog.isBlank()) {
            Messages.showWarningDialog(project, "분석할 콘솔 로그가 없습니다.", "AI Error Purifier");
            return;
        }
        if (rawLog.length() > 100_000) {
            Messages.showWarningDialog(project, "로그가 너무 깁니다. 핵심 오류 영역을 선택한 뒤 다시 실행하세요.", "AI Error Purifier");
            return;
        }

        showToolWindow(project, panel -> panel.showLoading(selectedText != null && !selectedText.isBlank()));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ApiService.PreparedPrompt response = apiService.preparePrompt(project, rawLog, selectedText);
                if (!response.analysisReady()) {
                    showToolWindow(project, panel -> panel.showInsufficientLog(response.guidance()));
                    return;
                }
                ErrorPurifierSettings settings = ErrorPurifierSettings.getInstance();
                LlmProvider provider = settings.selectedProvider();
                String apiKey = ApiKeyStore.get(provider)
                        .filter(key -> !key.isBlank())
                        .orElseThrow(() -> new IllegalStateException(provider.displayName() + " API 키를 Settings > Tools > AI Error Purifier에 등록하세요."));
                String model = settings.model.isBlank() ? provider.defaultModel() : settings.model;

                showToolWindow(project, panel -> panel.startStreaming(provider, model));
                StringBuilder answer = new StringBuilder();
                LlmClientService.LlmResult result = llmClientService.stream(provider, model, apiKey, response.preparedPrompt(), delta -> {
                    answer.append(delta);
                    showToolWindow(project, panel -> panel.appendStreamingText(delta));
                });
                Map<String, String> evidenceLines = extractEvidenceLines(response.preparedPrompt(), answer.toString());
                Long usageId = null;
                String usageError = null;
                try {
                    usageId = apiService.reportUsage(response, provider, model, result, java.util.List.copyOf(evidenceLines.keySet()), 0);
                } catch (Exception exception) {
                    usageError = exception.getMessage();
                }
                Long finalUsageId = usageId;
                java.util.function.BiConsumer<Integer, Boolean> feedbackHandler = finalUsageId == null ? null :
                        (rating, resolved) -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
                            try {
                                apiService.reportFeedback(finalUsageId, rating, resolved);
                                showToolWindow(project, ErrorPurifierToolWindowFactory.ErrorPurifierPanel::showFeedbackSaved);
                            } catch (Exception feedbackError) {
                                showToolWindow(project, p -> p.showFeedbackError(feedbackError.getMessage()));
                            }
                        });
                showToolWindow(project, panel -> panel.finishStreaming(result, evidenceLines, response.logTruncated(),
                        response.appliedRuleCounts(), response.protectedLineCount(), feedbackHandler,
                        feedbackType -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
                            try {
                                apiService.reportRefinementFeedback(response, feedbackType);
                                showToolWindow(project, ErrorPurifierToolWindowFactory.ErrorPurifierPanel::showRefinementFeedbackSaved);
                            } catch (Exception feedbackError) {
                                showToolWindow(project, p -> p.showFeedbackError(feedbackError.getMessage()));
                            }
                        })
                ));
                if (usageError != null) {
                    String finalUsageError = usageError;
                    showToolWindow(project, panel -> panel.showUsageReportError(finalUsageError));
                }
            } catch (Exception exception) {
                ApplicationManager.getApplication().invokeLater(() ->
                        Messages.showErrorDialog(project, "프롬프트 정제 요청에 실패했습니다.\n" + exception.getMessage(), "AI Error Purifier")
                );
            }
        });
    }

    private Map<String, String> extractEvidenceLines(String preparedPrompt, String answer) {
        Map<String, String> promptLines = new LinkedHashMap<>();
        java.util.regex.Matcher promptMatcher = java.util.regex.Pattern
                .compile("(?m)^(L\\d{3})\\s*\\|\\s?(.*)$")
                .matcher(preparedPrompt);
        while (promptMatcher.find()) {
            promptLines.put(promptMatcher.group(1), promptMatcher.group(2));
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("L\\d{3}").matcher(answer);
        Map<String, String> evidence = new LinkedHashMap<>();
        while (matcher.find()) {
            String lineNumber = matcher.group();
            if (promptLines.containsKey(lineNumber)) {
                evidence.putIfAbsent(lineNumber, promptLines.get(lineNumber));
            }
        }
        return evidence;
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
