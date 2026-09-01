package com.errorpurifier.service;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsentAwareHttpSenderTest {

    private static final HttpRequest REQUEST = HttpRequest.newBuilder(URI.create("https://example.invalid/test")).GET().build();

    @Test
    void declinedConsentBlocksBackendAndLlmTransports() {
        assertAllTransportsBlocked("");
    }

    @Test
    void previousPolicyVersionBlocksBackendAndLlmTransports() {
        assertAllTransportsBlocked("2026-01-01");
    }

    @Test
    void revocationAfterSchedulingStillBlocksBackendAndLlmTransports() {
        AtomicReference<String> storedVersion = new AtomicReference<>(PrivacyConsentService.CURRENT_POLICY_VERSION);
        CountingDelegate delegate = new CountingDelegate();
        ConsentAwareHttpSender sender = new ConsentAwareHttpSender(
                () -> PrivacyConsentService.hasCurrentConsent(storedVersion.get()),
                delegate
        );

        storedVersion.set("");

        assertThrows(ConsentAwareHttpSender.ConsentRequiredException.class, () -> sender.sendString(REQUEST));
        assertThrows(ConsentAwareHttpSender.ConsentRequiredException.class, () -> sender.sendLines(REQUEST));
        assertEquals(0, delegate.stringCalls);
        assertEquals(0, delegate.lineCalls);
    }

    @Test
    void currentConsentAllowsBackendAndLlmTransports() throws Exception {
        CountingDelegate delegate = new CountingDelegate();
        ConsentAwareHttpSender sender = new ConsentAwareHttpSender(
                () -> PrivacyConsentService.hasCurrentConsent(PrivacyConsentService.CURRENT_POLICY_VERSION),
                delegate
        );

        assertNull(sender.sendString(REQUEST));
        assertNull(sender.sendLines(REQUEST));
        assertEquals(1, delegate.stringCalls);
        assertEquals(1, delegate.lineCalls);
    }

    private void assertAllTransportsBlocked(String storedVersion) {
        CountingDelegate delegate = new CountingDelegate();
        ConsentAwareHttpSender sender = new ConsentAwareHttpSender(
                () -> PrivacyConsentService.hasCurrentConsent(storedVersion),
                delegate
        );

        assertThrows(ConsentAwareHttpSender.ConsentRequiredException.class, () -> sender.sendString(REQUEST));
        assertThrows(ConsentAwareHttpSender.ConsentRequiredException.class, () -> sender.sendLines(REQUEST));
        assertEquals(0, delegate.stringCalls);
        assertEquals(0, delegate.lineCalls);
    }

    private static final class CountingDelegate implements ConsentAwareHttpSender.Delegate {
        private int stringCalls;
        private int lineCalls;

        @Override
        public HttpResponse<String> sendString(HttpRequest request) {
            stringCalls++;
            return null;
        }

        @Override
        public HttpResponse<Stream<String>> sendLines(HttpRequest request) {
            lineCalls++;
            return null;
        }
    }
}
