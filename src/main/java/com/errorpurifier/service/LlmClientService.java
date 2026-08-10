package com.errorpurifier.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class LlmClientService {
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public LlmResult stream(LlmProvider provider, String model, String apiKey, String prompt, Consumer<String> onDelta) throws Exception {
        long startedAt = System.nanoTime();
        Usage usage = switch (provider) {
            case OPENAI -> streamOpenAi(model, apiKey, prompt, onDelta);
            case GEMINI -> streamGemini(model, apiKey, prompt, onDelta);
            case CLAUDE -> streamClaude(model, apiKey, prompt, onDelta);
        };
        return new LlmResult(usage.inputTokens, usage.outputTokens, usage.totalTokens,
                (System.nanoTime() - startedAt) / 1_000_000);
    }

    private Usage streamOpenAi(String model, String apiKey, String prompt, Consumer<String> onDelta) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("input", prompt);
        body.addProperty("stream", true);
        body.addProperty("store", false);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return consumeSse(request, onDelta, event -> {
            if ("response.output_text.delta".equals(type(event))) {
                onDelta.accept(event.get("delta").getAsString());
            }
            if ("response.completed".equals(type(event))) {
                JsonObject usage = event.getAsJsonObject("response").getAsJsonObject("usage");
                return usage == null ? null : new Usage(number(usage, "input_tokens"), number(usage, "output_tokens"), number(usage, "total_tokens"));
            }
            return null;
        });
    }

    private Usage streamGemini(String model, String apiKey, String prompt, Consumer<String> onDelta) throws Exception {
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject body = new JsonObject();
        body.add("contents", contents);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + URLEncoder.encode(model, StandardCharsets.UTF_8)
                + ":streamGenerateContent?alt=sse";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return consumeSse(request, onDelta, event -> {
            if (event.has("candidates") && !event.getAsJsonArray("candidates").isEmpty()) {
                JsonObject candidate = event.getAsJsonArray("candidates").get(0).getAsJsonObject();
                if (candidate.has("content") && candidate.getAsJsonObject("content").has("parts")) {
                    for (var element : candidate.getAsJsonObject("content").getAsJsonArray("parts")) {
                        JsonObject responsePart = element.getAsJsonObject();
                        if (responsePart.has("text")) onDelta.accept(responsePart.get("text").getAsString());
                    }
                }
            }
            if (event.has("usageMetadata")) {
                JsonObject usage = event.getAsJsonObject("usageMetadata");
                return new Usage(number(usage, "promptTokenCount"), number(usage, "candidatesTokenCount"), number(usage, "totalTokenCount"));
            }
            return null;
        });
    }

    private Usage streamClaude(String model, String apiKey, String prompt, Consumer<String> onDelta) throws Exception {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(message);
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 4096);
        body.addProperty("stream", true);
        body.add("messages", messages);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        final int[] inputTokens = {0};
        return consumeSse(request, onDelta, event -> {
            if ("message_start".equals(type(event))) {
                inputTokens[0] = number(event.getAsJsonObject("message").getAsJsonObject("usage"), "input_tokens");
            }
            if ("content_block_delta".equals(type(event)) && event.getAsJsonObject("delta").has("text")) {
                onDelta.accept(event.getAsJsonObject("delta").get("text").getAsString());
            }
            if ("message_delta".equals(type(event))) {
                int outputTokens = number(event.getAsJsonObject("usage"), "output_tokens");
                return new Usage(inputTokens[0], outputTokens, inputTokens[0] + outputTokens);
            }
            return null;
        });
    }

    private Usage consumeSse(HttpRequest request, Consumer<String> onDelta, EventConsumer eventConsumer) throws Exception {
        HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (Stream<String> lines = response.body()) {
                String body = lines.limit(20).collect(java.util.stream.Collectors.joining("\n"));
                throw new IOException("LLM 요청 실패 (" + response.statusCode() + "): "
                        + (body.isBlank() ? "응답 본문 없음" : extractErrorMessage(body)));
            }
        }
        Usage latestUsage = new Usage(0, 0, 0);
        try (Stream<String> lines = response.body()) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.equals("[DONE]") || data.isEmpty()) continue;
                JsonObject event = JsonParser.parseString(data).getAsJsonObject();
                Usage usage = eventConsumer.accept(event);
                if (usage != null) latestUsage = usage;
            }
        }
        return latestUsage;
    }

    private String type(JsonObject event) { return event.has("type") ? event.get("type").getAsString() : ""; }
    private int number(JsonObject value, String key) { return value != null && value.has(key) ? value.get(key).getAsInt() : 0; }

    private String extractErrorMessage(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error") && root.get("error").isJsonObject()) {
                JsonObject error = root.getAsJsonObject("error");
                if (error.has("message")) return error.get("message").getAsString();
            }
        } catch (RuntimeException ignored) { }
        return body.length() <= 1_000 ? body : body.substring(0, 1_000) + "…";
    }

    private interface EventConsumer { Usage accept(JsonObject event); }
    private record Usage(int inputTokens, int outputTokens, int totalTokens) { }
    public record LlmResult(int inputTokens, int outputTokens, int totalTokens, long latencyMs) { }
}
