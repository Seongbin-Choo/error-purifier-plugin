package com.errorpurifier.service;

import com.errorpurifier.ErrorPurifierBundle;
import com.intellij.ide.BrowserUtil;
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
    private JLabel privacyConsentStatusLabel;
    private JButton privacyConsentButton;
    private JButton privacyRevokeButton;

    @Override
    public @Nls String getDisplayName() {
        return ErrorPurifierBundle.message("plugin.title");
    }

    @Override
    public @Nullable JComponent createComponent() {
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
        panel.add(new JLabel(ErrorPurifierBundle.message("settings.backendUrl")));
        backendUrlField = new JTextField(ErrorPurifierSettings.getInstance().backendUrl);
        panel.add(backendUrlField);
        panel.add(new JLabel(ErrorPurifierBundle.message("settings.provider")));
        providerBox = new JComboBox<>(LlmProvider.values());
        providerBox.setSelectedItem(ErrorPurifierSettings.getInstance().selectedProvider());
        providerBox.addActionListener(event -> {
            LlmProvider selected = (LlmProvider) providerBox.getSelectedItem();
            modelField.setText(selected.defaultModel());
            apiKeyField.setText(ApiKeyStore.get(selected).orElse(""));
        });
        panel.add(providerBox);
        panel.add(new JLabel(ErrorPurifierBundle.message("settings.model")));
        modelField = new JTextField(ErrorPurifierSettings.getInstance().resolvedModel());
        panel.add(modelField);
        panel.add(new JLabel(ErrorPurifierBundle.message("settings.analysisMode")));
        analysisModeBox = new JComboBox<>(AnalysisMode.values());
        analysisModeBox.setSelectedItem(ErrorPurifierSettings.getInstance().selectedAnalysisMode());
        analysisModeDescriptionLabel = new JLabel();
        analysisModeBox.addActionListener(event -> updateAnalysisModeDescription());
        updateAnalysisModeDescription();
        panel.add(analysisModeBox);
        panel.add(analysisModeDescriptionLabel);
        panel.add(new JLabel(ErrorPurifierBundle.message("settings.apiKey")));
        apiKeyField = new JPasswordField(ApiKeyStore.get(ErrorPurifierSettings.getInstance().selectedProvider()).orElse(""));
        panel.add(apiKeyField);
        panel.add(new JLabel(ErrorPurifierBundle.message("settings.privacyConsent")));
        privacyConsentStatusLabel = new JLabel();
        panel.add(privacyConsentStatusLabel);
        JPanel privacyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        privacyConsentButton = new JButton();
        privacyConsentButton.addActionListener(event -> {
            PrivacyConsentService.reviewAndRequestConsent();
            updatePrivacyConsentControls();
        });
        privacyRevokeButton = new JButton(ErrorPurifierBundle.message("settings.privacy.revoke"));
        privacyRevokeButton.addActionListener(event -> revokePrivacyConsent());
        JButton openPrivacyPolicyButton = new JButton(ErrorPurifierBundle.message("settings.privacy.openPolicy"));
        openPrivacyPolicyButton.addActionListener(event -> BrowserUtil.browse(PrivacyConsentService.POLICY_URL));
        privacyPanel.add(privacyConsentButton);
        privacyPanel.add(privacyRevokeButton);
        privacyPanel.add(openPrivacyPolicyButton);
        panel.add(privacyPanel);
        updatePrivacyConsentControls();
        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        connectionTestButton = new JButton(ErrorPurifierBundle.message("settings.connection.test"));
        connectionTestButton.addActionListener(event -> testConnection());
        connectionStatusLabel = new JLabel(ErrorPurifierBundle.message("settings.connection.noLog"));
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
            updatePrivacyConsentControls();
        }
    }

    private void testConnection() {
        LlmProvider provider = (LlmProvider) providerBox.getSelectedItem();
        String model = modelField.getText().trim();
        String apiKey = String.valueOf(apiKeyField.getPassword()).trim();
        if (apiKey.isBlank()) {
            showConnectionStatus(ErrorPurifierBundle.message("settings.connection.apiKeyRequired"), Color.RED);
            return;
        }
        if (model.isBlank()) {
            showConnectionStatus(ErrorPurifierBundle.message("settings.connection.modelRequired"), Color.RED);
            return;
        }
        if (!PrivacyConsentService.scheduleWithConsent(null, () -> startConnectionTest(provider, model, apiKey))) {
            showConnectionStatus(ErrorPurifierBundle.message("settings.connection.consentRequired"), Color.DARK_GRAY);
            updatePrivacyConsentControls();
            return;
        }
        updatePrivacyConsentControls();
    }

    private void startConnectionTest(LlmProvider provider, String model, String apiKey) {
        connectionTestButton.setEnabled(false);
        showConnectionStatus(ErrorPurifierBundle.message("settings.connection.testing"), Color.DARK_GRAY);
        ModalityState modalityState = ModalityState.stateForComponent(connectionTestButton);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                new LlmClientService().verifyConnection(provider, model, apiKey);
                ApplicationManager.getApplication().invokeLater(() -> {
                    connectionTestButton.setEnabled(true);
                    showConnectionStatus(ErrorPurifierBundle.message("settings.connection.success"), new Color(40, 150, 40));
                    Messages.showInfoMessage(ErrorPurifierBundle.message("settings.connection.successDialog"),
                            ErrorPurifierBundle.message("plugin.title"));
                }, modalityState);
            } catch (Exception exception) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    connectionTestButton.setEnabled(true);
                    String message = UserMessageFormatter.llmConnectionFailure(exception);
                    showConnectionStatus(ErrorPurifierBundle.message("settings.connection.failure", message), Color.RED);
                    Messages.showErrorDialog(ErrorPurifierBundle.message("settings.connection.failureDialog", message),
                            ErrorPurifierBundle.message("plugin.title"));
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

    private void revokePrivacyConsent() {
        int result = Messages.showYesNoDialog(
                ErrorPurifierBundle.message("settings.privacy.revokeMessage"),
                ErrorPurifierBundle.message("settings.privacy.revokeTitle"),
                ErrorPurifierBundle.message("settings.privacy.revokeConfirm"),
                ErrorPurifierBundle.message("common.cancel"),
                Messages.getWarningIcon()
        );
        if (result == Messages.YES) {
            PrivacyConsentService.revokeConsent();
            updatePrivacyConsentControls();
        }
    }

    private void updatePrivacyConsentControls() {
        if (privacyConsentStatusLabel == null) {
            return;
        }
        boolean granted = PrivacyConsentService.isConsentGranted();
        privacyConsentStatusLabel.setText(ErrorPurifierBundle.message("settings.privacy.status", PrivacyConsentService.statusText()));
        privacyConsentStatusLabel.setForeground(granted ? new Color(40, 150, 40) : Color.DARK_GRAY);
        privacyConsentButton.setText(ErrorPurifierBundle.message(
                granted ? "settings.privacy.reviewAgain" : "settings.privacy.reviewGrant"));
        privacyRevokeButton.setEnabled(granted);
    }

}
