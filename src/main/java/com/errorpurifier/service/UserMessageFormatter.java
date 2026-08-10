package com.errorpurifier.service;

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
            return "연결 시간이 초과되었습니다. 네트워크를 확인한 뒤 다시 시도하세요.";
        }
        if (hasCause(exception, ConnectException.class) || hasCause(exception, UnknownHostException.class)) {
            return "AI 제공자에 연결할 수 없습니다. 네트워크 연결을 확인하세요.";
        }
        return switch (statusCode(exception)) {
            case 400 -> "API 키 또는 모델 설정이 올바르지 않습니다. 제공자와 모델명을 확인하세요.";
            case 401 -> "API 키가 올바르지 않거나 만료되었습니다. 공백 없이 다시 입력하세요.";
            case 403 -> "이 API 키에는 선택한 모델을 사용할 권한이 없습니다. 계정 권한과 모델을 확인하세요.";
            case 404 -> "입력한 모델을 찾을 수 없습니다. 제공자와 모델명을 확인하세요.";
            case 429 -> "요청 한도 또는 사용량 제한에 도달했습니다. 잠시 후 다시 시도하세요.";
            case 500, 501, 502, 503, 504 -> "AI 제공자에 일시적인 문제가 있습니다. 잠시 후 다시 시도하세요.";
            default -> "연결을 확인하지 못했습니다. API 키, 모델명 및 네트워크를 확인하세요.";
        };
    }

    public static String backendFailure(Exception exception) {
        if (hasCause(exception, HttpTimeoutException.class)) {
            return "백엔드 응답 시간이 초과되었습니다. 서버 상태를 확인한 뒤 다시 시도하세요.";
        }
        if (hasCause(exception, ConnectException.class) || hasCause(exception, UnknownHostException.class)) {
            return "백엔드에 연결할 수 없습니다. 백엔드 주소와 실행 상태를 확인하세요.";
        }
        return switch (statusCode(exception)) {
            case 401, 403 -> "백엔드 인증에 실패했습니다. IntelliJ를 재시작한 뒤 다시 시도하세요.";
            case 404 -> "백엔드 주소 또는 API 경로를 찾을 수 없습니다. 설정을 확인하세요.";
            case 429 -> "오늘의 요청 한도에 도달했습니다. 잠시 후 다시 시도하세요.";
            case 500, 501, 502, 503, 504 -> "백엔드에 일시적인 문제가 있습니다. 잠시 후 다시 시도하세요.";
            default -> "백엔드 요청을 완료하지 못했습니다. 주소와 서버 상태를 확인하세요.";
        };
    }

    public static String analysisFailure(Exception exception) {
        String message = exception.getMessage();
        if (message != null && message.contains("API 키를 Settings")) {
            return "선택한 AI 제공자의 API 키를 설정에 등록하세요.";
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
