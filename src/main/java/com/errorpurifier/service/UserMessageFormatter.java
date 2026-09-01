package com.errorpurifier.service;

import com.errorpurifier.ErrorPurifierBundle;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UserMessageFormatter {
    private static final Pattern HTTP_STATUS = Pattern.compile("\\((\\d{3})\\)");

    private UserMessageFormatter() {
    }

    public static String llmConnectionFailure(Exception exception) {
        if (hasCause(exception, HttpTimeoutException.class)) {
            return ErrorPurifierBundle.message("error.llm.timeout");
        }
        if (hasCause(exception, ConnectException.class) || hasCause(exception, UnknownHostException.class)) {
            return ErrorPurifierBundle.message("error.llm.unreachable");
        }
        return switch (statusCode(exception)) {
            case 400 -> ErrorPurifierBundle.message("error.llm.400");
            case 401 -> ErrorPurifierBundle.message("error.llm.401");
            case 403 -> ErrorPurifierBundle.message("error.llm.403");
            case 404 -> ErrorPurifierBundle.message("error.llm.404");
            case 429 -> ErrorPurifierBundle.message("error.llm.429");
            case 500, 501, 502, 503, 504 -> ErrorPurifierBundle.message("error.llm.5xx");
            default -> ErrorPurifierBundle.message("error.llm.default");
        };
    }

    public static String backendFailure(Exception exception) {
        if (hasCause(exception, HttpTimeoutException.class)) {
            return ErrorPurifierBundle.message("error.backend.timeout");
        }
        if (hasCause(exception, ConnectException.class) || hasCause(exception, UnknownHostException.class)) {
            return ErrorPurifierBundle.message("error.backend.unreachable");
        }
        return switch (statusCode(exception)) {
            case 401, 403 -> ErrorPurifierBundle.message("error.backend.auth");
            case 404 -> ErrorPurifierBundle.message("error.backend.notFound");
            case 429 -> ErrorPurifierBundle.message("error.backend.limit");
            case 500, 501, 502, 503, 504 -> ErrorPurifierBundle.message("error.backend.5xx");
            default -> ErrorPurifierBundle.message("error.backend.default");
        };
    }

    public static String analysisFailure(Exception exception) {
        String message = exception.getMessage();
        if (message != null && message.contains("API") && message.contains("Settings")) {
            return ErrorPurifierBundle.message("error.analysis.apiKey");
        }
        if (message != null && message.startsWith("LLM ")) {
            return llmConnectionFailure(exception);
        }
        return backendFailure(exception);
    }

    private static int statusCode(Exception exception) {
        String message = exception.getMessage();
        if (message == null) return 0;
        Matcher matcher = HTTP_STATUS.matcher(message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }
}
