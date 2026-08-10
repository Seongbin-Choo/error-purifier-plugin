package com.errorpurifier.service;

import com.errorpurifier.util.DeviceAuthManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApiService {

    private static final String PLUGIN_VERSION = "1.0.0";
    private final HttpClient httpClient;

    public ApiService() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public PreparedPrompt preparePrompt(Project project, String rawLog, String selectedText) throws Exception {
        String deviceId = ensureRegisteredDevice();
        JsonObject body = new JsonObject();
        body.addProperty("rawLog", rawLog);
        body.add("selectedText", selectedText == null ? JsonNull.INSTANCE : stringValue(selectedText));
        body.add("projectFiles", mapValue(ProjectContextCollector.collectProjectFiles(project)));
        body.add("environmentTags", mapValue(ProjectContextCollector.environmentTags()));

        JsonObject response = post("/prompt/prepare", body, deviceId);
        boolean analysisReady = response.get("analysisReady").getAsBoolean();
        Map<String, Integer> appliedRuleCounts = new LinkedHashMap<>();
        if (response.has("appliedRuleCounts") && response.get("appliedRuleCounts").isJsonObject()) {
            response.getAsJsonObject("appliedRuleCounts").entrySet()
                    .forEach(entry -> appliedRuleCounts.put(entry.getKey(), entry.getValue().getAsInt()));
        }
        return new PreparedPrompt(
                response.get("cacheHit").getAsBoolean(),
                response.get("cacheKey").getAsString(),
                response.get("exceptionType").getAsString(),
                analysisReady ? response.get("preparedPrompt").getAsString() : "",
                response.get("originalCharacters").getAsInt(),
                response.get("preparedCharacters").getAsInt(),
                analysisReady,
                response.has("guidance") && !response.get("guidance").isJsonNull() ? response.get("guidance").getAsString() : null,
                response.has("logTruncated") && response.get("logTruncated").getAsBoolean(),
                Map.copyOf(appliedRuleCounts),
                response.has("protectedLineCount") ? response.get("protectedLineCount").getAsInt() : 0
        );
    }

    public long reportUsage(PreparedPrompt prompt, LlmProvider provider, String model, LlmClientService.LlmResult result,
                            List<String> referencedLines, int rating) throws Exception {
        String deviceId = ensureRegisteredDevice();
        JsonObject body = new JsonObject();
        body.addProperty("provider", provider.name());
        body.addProperty("model", model);
        body.addProperty("cacheKey", prompt.cacheKey());
        body.addProperty("cacheHit", prompt.cacheHit());
        body.addProperty("promptHash", sha256(prompt.preparedPrompt()));
        body.addProperty("originalCharacters", prompt.originalCharacters());
        body.addProperty("preparedCharacters", prompt.preparedCharacters());
        body.addProperty("inputTokens", result.inputTokens());
        body.addProperty("outputTokens", result.outputTokens());
        body.addProperty("totalTokens", result.totalTokens());
        body.addProperty("latencyMs", result.latencyMs());
        body.addProperty("rating", rating);
        com.google.gson.JsonArray lines = new com.google.gson.JsonArray();
        referencedLines.forEach(lines::add);
        body.add("referencedLines", lines);
        return post("/usage", body, deviceId).get("usageId").getAsLong();
    }

    public void reportFeedback(long usageId, int rating, boolean resolved) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("rating", rating);
        body.addProperty("resolved", resolved);
        patch("/usage/" + usageId + "/feedback", body, ensureRegisteredDevice());
    }

    public void reportRefinementFeedback(PreparedPrompt prompt, String feedbackType) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("cacheKey", prompt.cacheKey());
        body.addProperty("feedbackType", feedbackType);
        body.addProperty("originalCharacters", prompt.originalCharacters());
        body.addProperty("preparedCharacters", prompt.preparedCharacters());
        body.add("appliedRuleCounts", integerMapValue(prompt.appliedRuleCounts()));
        body.addProperty("protectedLineCount", prompt.protectedLineCount());
        body.addProperty("logTruncated", prompt.logTruncated());
        post("/refinement-feedback", body, ensureRegisteredDevice());
    }

    /** Returns aggregates for this installation only; no raw logs, answers, or API keys are returned. */
    public UsageSummary getUsageSummary() throws Exception {
        JsonObject response = get("/usage/summary", ensureRegisteredDevice());
        return new UsageSummary(
                response.get("totalRequests").getAsLong(), response.get("helpfulResponses").getAsLong(),
                response.get("unhelpfulResponses").getAsLong(), response.get("resolvedResponses").getAsLong(),
                response.get("inputTokens").getAsLong(), response.get("outputTokens").getAsLong(),
                response.get("totalTokens").getAsLong(), response.get("originalCharacters").getAsLong(),
                response.get("preparedCharacters").getAsLong(), response.get("promptCharacterChangePercent").getAsDouble(),
                response.get("averageLatencyMs").getAsLong());
    }

    private String ensureRegisteredDevice() throws Exception {
        String existingId = DeviceAuthManager.readDeviceId().orElse("");
        try {
            return syncDevice(existingId);
        } catch (ApiException exception) {
            if (existingId.isBlank()) {
                throw exception;
            }
            // A removed server record or a copied/corrupt installation gets a new server-issued UUID.
            return syncDevice("");
        }
    }

    private String syncDevice(String deviceId) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("deviceUuid", deviceId);
        body.addProperty("pluginVersion", PLUGIN_VERSION);
        JsonObject response = post("/client/sync", body, null);
        String registeredId = response.get("deviceUuid").getAsString();
        DeviceAuthManager.saveDeviceId(registeredId);
        return registeredId;
    }

    private JsonObject post(String path, JsonObject body, String deviceId) throws IOException, InterruptedException, ApiException {
        return send("POST", path, body, deviceId);
    }

    private JsonObject patch(String path, JsonObject body, String deviceId) throws IOException, InterruptedException, ApiException {
        return send("PATCH", path, body, deviceId);
    }

    private JsonObject get(String path, String deviceId) throws IOException, InterruptedException, ApiException {
        return send("GET", path, null, deviceId);
    }

    private JsonObject send(String method, String path, JsonObject body, String deviceId) throws IOException, InterruptedException, ApiException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(ErrorPurifierSettings.getInstance().apiBaseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString()));
        if (body != null) {
            requestBuilder.header("Content-Type", "application/json");
        }
        if (deviceId != null) {
            requestBuilder.header("X-Device-UUID", deviceId);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String prefix = response.statusCode() == 429 ? "요청 한도 초과: " : "서버 요청 실패 (" + response.statusCode() + "): ";
            throw new ApiException(prefix + extractBackendMessage(response.body()));
        }
        // 피드백 저장처럼 본문이 없는 성공 응답도 정상 처리한다.
        if (response.body() == null || response.body().isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new ApiException("서버가 JSON 응답을 반환하지 않았습니다.");
        }
    }

    private JsonElement stringValue(String value) {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("value", value);
        return wrapper.get("value");
    }

    private JsonObject mapValue(Map<String, String> values) {
        JsonObject json = new JsonObject();
        values.forEach(json::addProperty);
        return json;
    }

    private JsonObject integerMapValue(Map<String, Integer> values) {
        JsonObject json = new JsonObject();
        values.forEach(json::addProperty);
        return json;
    }

    private String abbreviate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 500) + "…";
    }

    private String extractBackendMessage(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("detail") && !json.get("detail").isJsonNull()) return json.get("detail").getAsString();
            if (json.has("message") && !json.get("message").isJsonNull()) return json.get("message").getAsString();
        } catch (RuntimeException ignored) {
            // Some reverse proxies return HTML/plain text; keep a bounded response in that case.
        }
        return abbreviate(body);
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("프롬프트 해시를 만들 수 없습니다.", exception);
        }
    }

    public record PreparedPrompt(boolean cacheHit, String cacheKey, String exceptionType,
                                 String preparedPrompt, int originalCharacters, int preparedCharacters,
                                 boolean analysisReady, String guidance, boolean logTruncated,
                                 Map<String, Integer> appliedRuleCounts, int protectedLineCount) {
    }

    public record UsageSummary(long totalRequests, long helpfulResponses, long unhelpfulResponses,
                               long resolvedResponses, long inputTokens, long outputTokens, long totalTokens,
                               long originalCharacters, long preparedCharacters, double promptCharacterChangePercent,
                               long averageLatencyMs) {
    }

    private static final class ApiException extends Exception {
        private ApiException(String message) {
            super(message);
        }
    }
}
