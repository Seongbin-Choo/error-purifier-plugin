package com.errorpurifier.service;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JPasswordField;
import javax.swing.JComboBox;
import javax.swing.JButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;

public class ErrorPurifierConfigurable implements Configurable {

    private JTextField backendUrlField;
    private JComboBox<LlmProvider> providerBox;
    private JTextField modelField;
    private JComboBox<AnalysisMode> analysisModeBox;
    private JLabel analysisModeDescriptionLabel;
    private JPasswordField apiKeyField;
    private JButton connectionTestButton;
    private JLabel connectionStatusLabel;

    @Override
    public @Nls String getDisplayName() {
        return "AI Error Purifier";
    }

    @Override
    public @Nullable JComponent createComponent() {
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
        panel.add(new JLabel("Error Purifier Backend URL"));
        backendUrlField = new JTextField(ErrorPurifierSettings.getInstance().backendUrl);
        panel.add(backendUrlField);
        panel.add(new JLabel("LLM Provider"));
        providerBox = new JComboBox<>(LlmProvider.values());
        providerBox.setSelectedItem(ErrorPurifierSettings.getInstance().selectedProvider());
        providerBox.addActionListener(event -> {
            LlmProvider selected = (LlmProvider) providerBox.getSelectedItem();
            modelField.setText(selected.defaultModel());
            apiKeyField.setText(ApiKeyStore.get(selected).orElse(""));
        });
        panel.add(providerBox);
        panel.add(new JLabel("Model"));
        modelField = new JTextField(ErrorPurifierSettings.getInstance().resolvedModel());
        panel.add(modelField);
        panel.add(new JLabel("Analysis Mode"));
        analysisModeBox = new JComboBox<>(AnalysisMode.values());
        analysisModeBox.setSelectedItem(ErrorPurifierSettings.getInstance().selectedAnalysisMode());
        analysisModeDescriptionLabel = new JLabel();
        analysisModeBox.addActionListener(event -> updateAnalysisModeDescription());
        updateAnalysisModeDescription();
        panel.add(analysisModeBox);
        panel.add(analysisModeDescriptionLabel);
        panel.add(new JLabel("API Key (stored only in IntelliJ PasswordSafe)"));
        apiKeyField = new JPasswordField(ApiKeyStore.get(ErrorPurifierSettings.getInstance().selectedProvider()).orElse(""));
        panel.add(apiKeyField);
        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        connectionTestButton = new JButton("LLM 연결 테스트");
        connectionTestButton.addActionListener(event -> testConnection());
        connectionStatusLabel = new JLabel("  실제 오류 로그는 전송하지 않습니다.");
        connectionPanel.add(connectionTestButton);
        connectionPanel.add(connectionStatusLabel);
        panel.add(connectionPanel);
        return panel;
    }

    @Override
    public boolean isModified() {
        return backendUrlField != null && (!backendUrlField.getText().trim().equals(ErrorPurifierSettings.getInstance().backendUrl)
                || providerBox.getSelectedItem() != ErrorPurifierSettings.getInstance().selectedProvider()
                || !modelField.getText().trim().equals(ErrorPurifierSettings.getInstance().model)
                || analysisModeBox.getSelectedItem() != ErrorPurifierSettings.getInstance().selectedAnalysisMode()
                || !String.valueOf(apiKeyField.getPassword()).equals(ApiKeyStore.get((LlmProvider) providerBox.getSelectedItem()).orElse("")));
    }

    @Override
    public void apply() {
        ErrorPurifierSettings.getInstance().backendUrl = backendUrlField.getText().trim();
        LlmProvider provider = (LlmProvider) providerBox.getSelectedItem();
        ErrorPurifierSettings.getInstance().provider = provider.name();
        ErrorPurifierSettings.getInstance().model = modelField.getText().trim();
        ErrorPurifierSettings.getInstance().analysisMode = ((AnalysisMode) analysisModeBox.getSelectedItem()).name();
        ApiKeyStore.save(provider, String.valueOf(apiKeyField.getPassword()).trim());
    }

    @Override
    public void reset() {
        if (backendUrlField != null) {
            backendUrlField.setText(ErrorPurifierSettings.getInstance().backendUrl);
            providerBox.setSelectedItem(ErrorPurifierSettings.getInstance().selectedProvider());
            modelField.setText(ErrorPurifierSettings.getInstance().resolvedModel());
            analysisModeBox.setSelectedItem(ErrorPurifierSettings.getInstance().selectedAnalysisMode());
            updateAnalysisModeDescription();
            apiKeyField.setText(ApiKeyStore.get(ErrorPurifierSettings.getInstance().selectedProvider()).orElse(""));
        }
    }

    private void testConnection() {
        LlmProvider provider = (LlmProvider) providerBox.getSelectedItem();
        String model = modelField.getText().trim();
        String apiKey = String.valueOf(apiKeyField.getPassword()).trim();
        if (apiKey.isBlank()) {
            showConnectionStatus("API 키를 입력하세요.", Color.RED);
            return;
        }
        if (model.isBlank()) {
            showConnectionStatus("모델명을 입력하세요.", Color.RED);
            return;
        }

        connectionTestButton.setEnabled(false);
        showConnectionStatus("연결 확인 중…", Color.DARK_GRAY);
        ModalityState modalityState = ModalityState.stateForComponent(connectionTestButton);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                new LlmClientService().verifyConnection(provider, model, apiKey);
                ApplicationManager.getApplication().invokeLater(() -> {
                    connectionTestButton.setEnabled(true);
                    showConnectionStatus("연결 성공 · API 키와 모델을 확인했습니다.", new Color(40, 150, 40));
                    Messages.showInfoMessage("API 키와 모델 연결을 확인했습니다.", "AI Error Purifier");
                }, modalityState);
            } catch (Exception exception) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    connectionTestButton.setEnabled(true);
                    String message = UserMessageFormatter.llmConnectionFailure(exception);
                    showConnectionStatus("연결 실패: " + message, Color.RED);
                    Messages.showErrorDialog("LLM 연결에 실패했습니다.\n" + message, "AI Error Purifier");
                }, modalityState);
            }
        });
    }

    private void showConnectionStatus(String message, Color color) {
        connectionStatusLabel.setText("  " + message);
        connectionStatusLabel.setForeground(color);
    }

    private void updateAnalysisModeDescription() {
        AnalysisMode mode = (AnalysisMode) analysisModeBox.getSelectedItem();
        if (mode != null) {
            analysisModeDescriptionLabel.setText("  " + mode.description());
        }
    }

}
