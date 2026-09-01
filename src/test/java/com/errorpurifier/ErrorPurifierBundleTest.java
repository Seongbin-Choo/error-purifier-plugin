package com.errorpurifier;

import com.errorpurifier.service.AnalysisMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorPurifierBundleTest {

    @Test
    void englishAndKoreanBundlesHaveIdenticalKeys() throws IOException {
        Properties english = load("messages/ErrorPurifierBundle.properties");
        Properties korean = load("messages/ErrorPurifierBundle_ko.properties");

        assertEquals(new HashSet<>(english.stringPropertyNames()), new HashSet<>(korean.stringPropertyNames()));
    }

    @Test
    void defaultBundleIsEnglishAndKoreanBundleContainsTranslation() throws IOException {
        Properties english = load("messages/ErrorPurifierBundle.properties");
        Properties korean = load("messages/ErrorPurifierBundle_ko.properties");

        assertEquals("Analyze Error Log with AI (Cost Optimized)", english.getProperty("action.PurifyErrorLogAction.text"));
        assertEquals("에러 로그 AI 분석 (비용 최적화)", korean.getProperty("action.PurifyErrorLogAction.text"));
        assertTrue(english.getProperty("analysis.mode.fast.prompt").startsWith("Answer in English."));
        assertTrue(korean.getProperty("analysis.mode.fast.prompt").startsWith("한국어로 답하세요."));
        assertTrue(english.getProperty("guidance.buildWrapperOnly").startsWith("The console contains only a Gradle"));
        assertTrue(korean.getProperty("guidance.buildWrapperOnly").startsWith("현재 콘솔에는 Gradle"));
    }

    @Test
    void everyParameterizedMessageCanBeFormatted() throws IOException {
        Object[] arguments = new Object[20];
        java.util.Arrays.fill(arguments, "value");

        for (String resource : Set.of("messages/ErrorPurifierBundle.properties", "messages/ErrorPurifierBundle_ko.properties")) {
            Properties properties = load(resource);
            for (String key : properties.stringPropertyNames()) {
                String formatted = new MessageFormat(properties.getProperty(key), Locale.ENGLISH).format(arguments);
                assertFalse(formatted.matches(".*\\{\\d+}.*"), () -> "Unformatted placeholder in " + resource + ": " + key);
            }
        }

        assertEquals("Add the Gemini API key under Settings | Tools | AI Error Purifier.",
                ErrorPurifierBundle.message("action.error.apiKeyMissing", "Gemini"));
    }

    @Test
    void analysisModesUseLocalizedBundleTextAndPreserveProviderLimits() {
        assertEquals("Fast", AnalysisMode.FAST.displayName());
        assertTrue(AnalysisMode.FAST.promptInstruction().startsWith("Answer in English."));
        assertEquals("minimal", AnalysisMode.FAST.geminiThinkingLevel());
        assertEquals(0, AnalysisMode.FAST.geminiThinkingBudget());
        assertEquals(1_200, AnalysisMode.FAST.maxOutputTokens());
        assertEquals(8_192, AnalysisMode.DEEP.geminiThinkingBudget());
        assertEquals(4_096, AnalysisMode.DEEP.maxOutputTokens());
    }

    private Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing test resource: " + resource);
            }
            properties.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        }
        return properties;
    }
}
