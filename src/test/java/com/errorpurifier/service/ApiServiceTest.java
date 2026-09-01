package com.errorpurifier.service;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServiceTest {

    @Test
    void parsesKnownGuidanceCodeAndUsesEnglishBundleByDefault() {
        JsonObject response = unreadyResponse();
        response.addProperty("guidanceCode", "BUILD_WRAPPER_ONLY");
        response.addProperty("guidance", "legacy Korean guidance");

        ApiService.PreparedPrompt prompt = ApiService.parsePreparedPrompt(response);

        assertEquals("BUILD_WRAPPER_ONLY", prompt.guidanceCode());
        assertEquals("guidance.buildWrapperOnly", prompt.guidanceMessageKey());
        assertTrue(prompt.localizedGuidance().startsWith("The console contains only a Gradle"));
    }

    @Test
    void missingOrUnknownGuidanceCodeFallsBackToLegacyGuidance() {
        JsonObject missingCode = unreadyResponse();
        missingCode.addProperty("guidance", "legacy missing-code guidance");
        ApiService.PreparedPrompt missingPrompt = ApiService.parsePreparedPrompt(missingCode);

        JsonObject unknownCode = unreadyResponse();
        unknownCode.addProperty("guidanceCode", "FUTURE_SERVER_CODE");
        unknownCode.addProperty("guidance", "future fallback guidance");
        ApiService.PreparedPrompt unknownPrompt = ApiService.parsePreparedPrompt(unknownCode);

        assertNull(missingPrompt.guidanceMessageKey());
        assertEquals("legacy missing-code guidance", missingPrompt.localizedGuidance());
        assertNull(unknownPrompt.guidanceMessageKey());
        assertEquals("future fallback guidance", unknownPrompt.localizedGuidance());
    }

    private JsonObject unreadyResponse() {
        JsonObject response = new JsonObject();
        response.addProperty("cacheHit", false);
        response.addProperty("cacheKey", "a".repeat(64));
        response.addProperty("exceptionType", "UNKNOWN");
        response.addProperty("preparedPrompt", "");
        response.addProperty("refinedLog", "BUILD FAILED");
        response.addProperty("originalCharacters", 12);
        response.addProperty("refinedCharacters", 12);
        response.addProperty("preparedCharacters", 12);
        response.addProperty("analysisReady", false);
        return response;
    }
}
