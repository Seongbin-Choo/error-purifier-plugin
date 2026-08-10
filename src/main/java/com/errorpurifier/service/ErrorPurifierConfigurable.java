package com.errorpurifier.service;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.application.ApplicationManager;
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
        modelField = new JTextField(ErrorPurifierSettings.getInstance().model);
        panel.add(modelField);
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
                || !String.valueOf(apiKeyField.getPassword()).equals(ApiKeyStore.get((LlmProvider) providerBox.getSelectedItem()).orElse("")));
    }

    @Override
    public void apply() {
        ErrorPurifierSettings.getInstance().backendUrl = backendUrlField.getText().trim();
        LlmProvider provider = (LlmProvider) providerBox.getSelectedItem();
        ErrorPurifierSettings.getInstance().provider = provider.name();
        ErrorPurifierSettings.getInstance().model = modelField.getText().trim();
        ApiKeyStore.save(provider, String.valueOf(apiKeyField.getPassword()).trim());
    }

    @Override
    public void reset() {
        if (backendUrlField != null) {
            backendUrlField.setText(ErrorPurifierSettings.getInstance().backendUrl);
            providerBox.setSelectedItem(ErrorPurifierSettings.getInstance().selectedProvider());
            modelField.setText(ErrorPurifierSettings.getInstance().model);
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
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                LlmClientService.LlmResult result = new LlmClientService().stream(provider, model, apiKey,
                        "Reply with exactly: OK", ignored -> { });
                ApplicationManager.getApplication().invokeLater(() -> {
                    connectionTestButton.setEnabled(true);
                    showConnectionStatus("연결 성공 · 입력 " + result.inputTokens() + " / 출력 "
                            + result.outputTokens() + " 토큰", new Color(40, 150, 40));
                });
            } catch (Exception exception) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    connectionTestButton.setEnabled(true);
                    showConnectionStatus("연결 실패: " + conciseMessage(exception), Color.RED);
                });
            }
        });
    }

    private void showConnectionStatus(String message, Color color) {
        connectionStatusLabel.setText("  " + message);
        connectionStatusLabel.setForeground(color);
    }

    private String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        int lineEnd = message.indexOf('\n');
        String firstLine = lineEnd >= 0 ? message.substring(0, lineEnd) : message;
        return firstLine.length() <= 200 ? firstLine : firstLine.substring(0, 200) + "…";
    }
}
