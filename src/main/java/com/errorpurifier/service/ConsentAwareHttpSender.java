package com.errorpurifier.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * Final, non-interactive privacy gate shared by every backend and LLM HTTP request.
 * Consent dialogs belong to the EDT call sites; this class only refuses transmission.
 */
final class ConsentAwareHttpSender {

    private final BooleanSupplier currentConsent;
    private final Delegate delegate;

    static ConsentAwareHttpSender using(HttpClient httpClient) {
        return new ConsentAwareHttpSender(PrivacyConsentService::isConsentGranted, new Delegate() {
            @Override
            public HttpResponse<String> sendString(HttpRequest request) throws IOException, InterruptedException {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            }

            @Override
            public HttpResponse<Stream<String>> sendLines(HttpRequest request) throws IOException, InterruptedException {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            }
        });
    }

    ConsentAwareHttpSender(BooleanSupplier currentConsent, Delegate delegate) {
        this.currentConsent = currentConsent;
        this.delegate = delegate;
    }

    HttpResponse<String> sendString(HttpRequest request) throws IOException, InterruptedException {
        requireCurrentConsent();
        return delegate.sendString(request);
    }

    HttpResponse<Stream<String>> sendLines(HttpRequest request) throws IOException, InterruptedException {
        requireCurrentConsent();
        return delegate.sendLines(request);
    }

    private void requireCurrentConsent() throws ConsentRequiredException {
        if (!currentConsent.getAsBoolean()) {
            throw new ConsentRequiredException();
        }
    }

    interface Delegate {
        HttpResponse<String> sendString(HttpRequest request) throws IOException, InterruptedException;

        HttpResponse<Stream<String>> sendLines(HttpRequest request) throws IOException, InterruptedException;
    }

    static final class ConsentRequiredException extends IOException {
        ConsentRequiredException() {
            super("Privacy consent is required before sending data. Review consent under Settings > Tools > AI Error Purifier.");
        }
    }
}
