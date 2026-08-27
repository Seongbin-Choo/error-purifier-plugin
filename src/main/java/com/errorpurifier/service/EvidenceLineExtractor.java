package com.errorpurifier.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 프롬프트에 매겨진 L### 줄 번호와 답변이 인용한 번호를 대조해 실제 근거 줄만 추린다.
 * 줄 번호는 세 자리로 시작하지만 1000줄을 넘기면 자릿수가 늘어나므로,
 * 세 자리에서 끊지 않고 토큰 경계까지 확인해야 L1000을 L100으로 오인하지 않는다.
 */
public final class EvidenceLineExtractor {

    private static final Pattern PROMPT_LINE = Pattern.compile("(?m)^(L\\d{3,})\\s*\\|\\s?(.*)$");
    private static final Pattern CITED_LINE = Pattern.compile("(?<![A-Za-z0-9])L\\d{3,}");

    private EvidenceLineExtractor() {
    }

    public static Map<String, String> extract(String preparedPrompt, String answer) {
        Map<String, String> promptLines = new LinkedHashMap<>();
        Matcher promptMatcher = PROMPT_LINE.matcher(preparedPrompt);
        while (promptMatcher.find()) {
            promptLines.put(promptMatcher.group(1), promptMatcher.group(2));
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        Matcher citedMatcher = CITED_LINE.matcher(answer);
        while (citedMatcher.find()) {
            String lineNumber = citedMatcher.group();
            if (promptLines.containsKey(lineNumber)) {
                evidence.putIfAbsent(lineNumber, promptLines.get(lineNumber));
            }
        }
        return evidence;
    }
}
