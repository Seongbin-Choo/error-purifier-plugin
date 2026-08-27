package com.errorpurifier.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerGroundingValidatorTest {

    @Test
    void warnsWhenAnExecutionTrailerIsConnectedToATestFailure() {
        List<String> warnings = AnswerGroundingValidator.validate(
                "200 OK\\n[실행 환경 메타데이터] 종료 코드 1(으)로 완료된 프로세스",
                "프로세스가 종료 코드 1로 완료되었고, 테넌트 검증 실패 결과일 가능성이 있습니다.",
                Map.of());

        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("종료 원인은 알 수 없으며")));
    }

    @Test
    void warnsWhenCarrierThreadIsUsedAsThreadLocalLeakEvidenceDespiteCacheMismatch() {
        String log = """
                Resolved tenantId=jeju-solar-co from request header X-Tenant-Id
                Resolved tenantId=seogwipo-wind-farm from request header X-Tenant-Id
                Returning cached tenant context result: tenantId=jeju-solar-co (cache populated at 12:30:01.108 on carrier thread pool-3-thread-7)
                """;
        String answer = "캐리어 스레드가 재사용될 때 TenantContextHolder ThreadLocal 상태가 초기화되지 않아 오염된 것입니다.";

        List<String> warnings = AnswerGroundingValidator.validate(log, answer, Map.of());

        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("캐시 키 격리가 1차 의심")));
    }
}
