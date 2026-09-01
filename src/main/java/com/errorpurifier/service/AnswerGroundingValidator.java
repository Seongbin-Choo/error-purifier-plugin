package com.errorpurifier.service;

import com.errorpurifier.ErrorPurifierBundle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnswerGroundingValidator {

    private static final String NOT_NEGATED_CAUSAL_VERB = "(?<!did not )(?<!didn['’]t )(?<!does not )(?<!doesn['’]t )"
            + "(?<!is not )(?<!isn['’]t )(?<!was not )(?<!wasn['’]t )(?<!can not )(?<!cannot )"
            + "(?<!can['’]t )(?<!could not )(?<!couldn['’]t )";
    private static final Pattern CITED_LINE = Pattern.compile("(?<![A-Za-z0-9])L\\d{3,}");
    private static final Pattern NORMAL_OUTCOME = Pattern.compile("(?i)(?:\\b200\\s+OK\\b|정상 응답|예외 없음|no exception)");
    private static final Pattern EXIT_TO_CAUSAL_ASSERTION = Pattern.compile(
            "(?is)(?:(?:exit\\s*code|종료\\s*코드).{0,300}(?:비정상(?:\\s+프로세스)?\\s*종료|크래시|강제\\s*종료|검증\\s*실패)"
                    + "|(?:비정상(?:\\s+프로세스)?\\s*종료|크래시|강제\\s*종료|검증\\s*실패).{0,300}(?:exit\\s*code|종료\\s*코드)"
                    + "|exit\\s*code[^.!?\\r\\n]{0,100}" + NOT_NEGATED_CAUSAL_VERB
                    + "(?:cause(?:d|s)?|causing|led\\s+to|result(?:ed|s)?\\s+in|trigger(?:ed|s)?)\\s+(?:the\\s+)?"
                    + "(?:crash|failure|failed\\s+(?:build|test|process)|abnormal\\s+termination)"
                    + "|(?:crash|failure|abnormal\\s+termination).{0,100}(?:was\\s+caused\\s+by|resulted\\s+from|was\\s+due\\s+to).{0,100}exit\\s*code)"
    );
    private static final Pattern RESOLVED_TENANT = Pattern.compile("(?m)Resolved tenantId=([^\\s]+)");
    private static final Pattern CACHED_TENANT = Pattern.compile("(?m)Returning cached tenant context result: tenantId=([^\\s]+)");
    private static final Pattern CARRIER_THREAD_LOCAL_CAUSAL_ASSERTION = Pattern.compile(
            "(?is)(?:(?:캐리어\\s*스레드|carrier\\s*thread).{0,250}(?:threadlocal|테넌트\\s*컨텍스트).{0,250}(?:재사용|바인딩|전파|유실|오염|초기화)"
                    + "|(?:threadlocal|테넌트\\s*컨텍스트).{0,250}(?:재사용|바인딩|전파|유실|오염|초기화).{0,250}(?:캐리어\\s*스레드|carrier\\s*thread)"
                    + "|carrier\\s*thread[^.!?\\r\\n]{0,120}" + NOT_NEGATED_CAUSAL_VERB
                    + "(?:reuse(?:d|s)?|retain(?:ed|s)?|propagat(?:ed|es)|carried|leak(?:ed|s)?)\\s+"
                    + "(?:a\\s+)?(?:stale\\s+|contaminated\\s+|corrupted\\s+)?threadlocal(?:\\s+state)?)"
    );

    private AnswerGroundingValidator() {
    }

    public static List<String> validate(String refinedLog, String answer, Map<String, String> evidenceLines) {
        List<String> warnings = new ArrayList<>();
        Set<String> citedLines = citedLines(answer);
        Set<String> missingLines = new LinkedHashSet<>(citedLines);
        missingLines.removeAll(evidenceLines.keySet());
        if (!missingLines.isEmpty()) {
            warnings.add(ErrorPurifierBundle.message("grounding.missingEvidence", String.join(", ", missingLines)));
        }

        boolean hasExecutionMetadata = refinedLog != null && refinedLog.contains("[실행 환경 메타데이터");
        boolean tiesExitCodeToFailure = EXIT_TO_CAUSAL_ASSERTION.matcher(answer).find();
        if (hasExecutionMetadata && tiesExitCodeToFailure) {
            warnings.add(ErrorPurifierBundle.message("grounding.exitCode"));
        }
        if (refinedLog != null && NORMAL_OUTCOME.matcher(refinedLog).find() && tiesExitCodeToFailure) {
            warnings.add(ErrorPurifierBundle.message("grounding.normalConflict"));
        }
        if (hasCrossTenantCacheEvidence(refinedLog) && CARRIER_THREAD_LOCAL_CAUSAL_ASSERTION.matcher(answer).find()) {
            warnings.add(ErrorPurifierBundle.message("grounding.crossTenantCache"));
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
