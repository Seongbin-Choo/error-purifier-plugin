package com.errorpurifier.service;

import com.errorpurifier.ErrorPurifierBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.util.function.BooleanSupplier;

public final class PrivacyConsentService {

    public static final String CURRENT_POLICY_VERSION = "2026-08-31";
    public static final String POLICY_URL = "https://github.com/Seongbin-Choo/error-purifier-plugin/blob/main/PRIVACY.md";

    private PrivacyConsentService() {
    }

    public static boolean ensureConsent(@Nullable Project project) {
        if (isConsentGranted()) {
            return true;
        }
        // Network work can reach this service from pooled threads after a queued task.
        // Never open a modal consent dialog from those defense-in-depth checks.
        if (!SwingUtilities.isEventDispatchThread()) {
            return false;
        }
        return showConsentDialog(project);
    }

    public static boolean scheduleWithConsent(@Nullable Project project, Runnable scheduler) {
        return scheduleIfConsented(() -> ensureConsent(project), scheduler);
    }

    public static boolean reviewAndRequestConsent() {
        return showConsentDialog(null);
    }

    public static synchronized boolean isConsentGranted() {
        return hasCurrentConsent(ErrorPurifierSettings.getInstance().privacyConsentVersion);
    }

    public static synchronized void revokeConsent() {
        ErrorPurifierSettings.getInstance().privacyConsentVersion = "";
    }

    public static String statusText() {
        return statusText(ErrorPurifierSettings.getInstance().privacyConsentVersion);
    }

    static boolean hasCurrentConsent(@Nullable String storedVersion) {
        return CURRENT_POLICY_VERSION.equals(storedVersion);
    }

    static String statusText(@Nullable String storedVersion) {
        if (hasCurrentConsent(storedVersion)) {
            return ErrorPurifierBundle.message("consent.status.granted", CURRENT_POLICY_VERSION);
        }
        if (storedVersion == null || storedVersion.isBlank()) {
            return ErrorPurifierBundle.message("consent.status.notGranted");
        }
        return ErrorPurifierBundle.message("consent.status.outdated", storedVersion);
    }

    static boolean scheduleIfConsented(BooleanSupplier consentRequest, Runnable scheduler) {
        if (!consentRequest.getAsBoolean()) {
            return false;
        }
        scheduler.run();
        return true;
    }

    private static boolean showConsentDialog(@Nullable Project project) {
        int result = Messages.showYesNoDialog(
                project,
                ErrorPurifierBundle.message("consent.message", POLICY_URL),
                ErrorPurifierBundle.message("consent.title"),
                ErrorPurifierBundle.message("consent.accept"),
                ErrorPurifierBundle.message("consent.decline"),
                Messages.getQuestionIcon()
        );
        if (result != Messages.YES) {
            return false;
        }
        storeCurrentConsent();
        return true;
    }

    private static synchronized void storeCurrentConsent() {
        ErrorPurifierSettings.getInstance().privacyConsentVersion = CURRENT_POLICY_VERSION;
    }
}
