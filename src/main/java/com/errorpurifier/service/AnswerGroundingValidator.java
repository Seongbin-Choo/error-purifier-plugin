package com.errorpurifier.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnswerGroundingValidator {

    private static final Pattern CITED_LINE = Pattern.compile("(?<![A-Za-z0-9])L\\d{3,}");
    private static final Pattern NORMAL_OUTCOME = Pattern.compile("(?i)(?:\\b200\\s+OK\\b|정상 응답|예외 없음|no exception)");
    private static final Pattern EXIT_TO_CAUSAL_ASSERTION = Pattern.compile("(?is)(?:(?:exit\\s*code|종료\\s*코드).{0,300}(?:비정상(?:\\s+프로세스)?\\s*종료|크래시|강제\\s*종료|검증\\s*실패)|(?:비정상(?:\\s+프로세스)?\\s*종료|크래시|강제\\s*종료|검증\\s*실패).{0,300}(?:exit\\s*code|종료\\s*코드))");
    private static final Pattern RESOLVED_TENANT = Pattern.compile("(?m)Resolved tenantId=([^\\s]+)");
    private static final Pattern CACHED_TENANT = Pattern.compile("(?m)Returning cached tenant context result: tenantId=([^\\s]+)");
    private static final Pattern CARRIER_THREAD_LOCAL_CAUSAL_ASSERTION = Pattern.compile("(?is)(?:(?:캐리어\\s*스레드|carrier\\s*thread).{0,250}(?:threadlocal|테넌트\\s*컨텍스트).{0,250}(?:재사용|바인딩|전파|유실|오염|초기화)|(?:threadlocal|테넌트\\s*컨텍스트).{0,250}(?:재사용|바인딩|전파|유실|오염|초기화).{0,250}(?:캐리어\\s*스레드|carrier\\s*thread))");

    private AnswerGroundingValidator() {
    }

    public static List<String> validate(String refinedLog, String answer, Map<String, String> evidenceLines) {
        List<String> warnings = new ArrayList<>();
        Set<String> citedLines = citedLines(answer);
        Set<String> missingLines = new LinkedHashSet<>(citedLines);
        missingLines.removeAll(evidenceLines.keySet());
        if (!missingLines.isEmpty()) {
            warnings.add("답변이 실제 로그에 없는 근거 번호를 인용했습니다: " + String.join(", ", missingLines));
        }

        boolean hasExecutionMetadata = refinedLog != null && refinedLog.contains("[실행 환경 메타데이터");
        boolean tiesExitCodeToFailure = EXIT_TO_CAUSAL_ASSERTION.matcher(answer).find();
        if (hasExecutionMetadata && tiesExitCodeToFailure) {
            warnings.add("실행 환경 종료 코드를 오류와 연결했습니다. 이 로그만으로 종료 원인은 알 수 없으며 별도 애플리케이션 종료 로그가 필요합니다.");
        }
        if (refinedLog != null && NORMAL_OUTCOME.matcher(refinedLog).find() && tiesExitCodeToFailure) {
            warnings.add("로그의 정상 처리 신호와 비정상 종료 단정이 상충할 수 있습니다.");
        }
        if (hasCrossTenantCacheEvidence(refinedLog) && CARRIER_THREAD_LOCAL_CAUSAL_ASSERTION.matcher(answer).find()) {
            warnings.add("서로 다른 테넌트가 같은 캐시 결과를 참조한 로그에서는 캐시 키 격리가 1차 의심입니다. carrier thread 이름만으로 ThreadLocal 오염을 원인으로 단정할 수 없습니다.");
        }
        return List.copyOf(warnings);
    }

    private static boolean hasCrossTenantCacheEvidence(String refinedLog) {
        if (refinedLog == null) {
            return false;
        }
        Set<String> resolvedTenants = matchedValues(RESOLVED_TENANT, refinedLog);
        Set<String> cachedTenants = matchedValues(CACHED_TENANT, refinedLog);
        return resolvedTenants.size() > 1 && cachedTenants.size() == 1 && resolvedTenants.containsAll(cachedTenants);
    }

    private static Set<String> matchedValues(Pattern pattern, String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static Set<String> citedLines(String answer) {
        Set<String> cited = new LinkedHashSet<>();
        Matcher matcher = CITED_LINE.matcher(answer);
        while (matcher.find()) {
            cited.add(matcher.group());
        }
        return cited;
    }
}
