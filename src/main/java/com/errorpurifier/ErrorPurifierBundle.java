package com.errorpurifier;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ErrorPurifierBundle extends DynamicBundle {
    public static final String BUNDLE = "messages.ErrorPurifierBundle";
    private static final ErrorPurifierBundle INSTANCE = new ErrorPurifierBundle();

    private ErrorPurifierBundle() {
        super(BUNDLE);
    }

    public static @NotNull String message(
            @NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
            Object @NotNull ... params
    ) {
        return INSTANCE.getMessage(key, params);
    }
}
