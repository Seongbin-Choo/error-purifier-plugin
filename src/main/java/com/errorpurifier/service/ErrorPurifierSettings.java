package com.errorpurifier.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "ErrorPurifierSettings", storages = @Storage("errorPurifier.xml"))
@Service(Service.Level.APP)
public final class ErrorPurifierSettings implements PersistentStateComponent<ErrorPurifierSettings> {

    public String backendUrl = "http://localhost:8080";
    public String provider = LlmProvider.GEMINI.name();
    public String model = LlmProvider.GEMINI.defaultModel();

    public static ErrorPurifierSettings getInstance() {
        return ApplicationManager.getApplication().getService(ErrorPurifierSettings.class);
    }

    @Override
    public @Nullable ErrorPurifierSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ErrorPurifierSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public String apiBaseUrl() {
        return backendUrl.replaceAll("/+$", "") + "/api/v1";
    }

    public LlmProvider selectedProvider() {
        try {
            return LlmProvider.valueOf(provider);
        } catch (IllegalArgumentException exception) {
            return LlmProvider.GEMINI;
        }
    }
}
