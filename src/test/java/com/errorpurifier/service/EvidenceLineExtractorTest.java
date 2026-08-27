package com.errorpurifier.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceLineExtractorTest {

    @Test
    void matchesCitedLineNumbersAgainstThePrompt() {
        String prompt = """
                L001 | at com.example.Service.load(Service.java:42)
                L002 | Caused by: java.lang.NullPointerException
                L003 | at com.example.Repository.find(Repository.java:11)
                """;

        Map<String, String> evidence = EvidenceLineExtractor.extract(prompt, "근거 로그: [L002]");

        assertEquals(Map.of("L002", "Caused by: java.lang.NullPointerException"), evidence);
    }

    @Test
    void doesNotAttributeAFourDigitCitationToAThreeDigitLine() {
        StringBuilder prompt = new StringBuilder();
        for (int line = 1; line <= 1000; line++) {
            prompt.append(String.format("L%03d | line-%d%n", line, line));
        }

        Map<String, String> evidence = EvidenceLineExtractor.extract(prompt.toString(), "근거 로그: [L1000]");

        assertEquals(Map.of("L1000", "line-1000"), evidence);
    }

    @Test
    void ignoresLineNumbersThatArePartOfAnotherToken() {
        String prompt = "L001 | boom\n";

        assertTrue(EvidenceLineExtractor.extract(prompt, "AL001 은 근거 인용이 아닙니다").isEmpty());
    }

    @Test
    void ignoresCitationsThatAreNotInThePrompt() {
        String prompt = "L001 | boom\n";

        assertTrue(EvidenceLineExtractor.extract(prompt, "근거 로그: [L999]").isEmpty());
    }
}
