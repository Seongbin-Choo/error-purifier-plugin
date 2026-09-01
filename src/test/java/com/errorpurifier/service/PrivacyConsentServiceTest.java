package com.errorpurifier.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyConsentServiceTest {

    @Test
    void currentPolicyVersionIsAccepted() {
        assertTrue(PrivacyConsentService.hasCurrentConsent(PrivacyConsentService.CURRENT_POLICY_VERSION));
    }

    @Test
    void missingOrOldPolicyVersionRequiresConsent() {
        assertFalse(PrivacyConsentService.hasCurrentConsent(null));
        assertFalse(PrivacyConsentService.hasCurrentConsent(""));
        assertFalse(PrivacyConsentService.hasCurrentConsent("2026-01-01"));
    }

    @Test
    void statusExplainsWhyConsentIsRequired() {
        assertEquals("Not granted", PrivacyConsentService.statusText(""));
        assertEquals(
                "Re-consent required (previous policy 2026-01-01)",
                PrivacyConsentService.statusText("2026-01-01")
        );
    }

    @Test
    void declinedConsentDoesNotScheduleNetworkWork() {
        AtomicInteger scheduled = new AtomicInteger();

        boolean accepted = PrivacyConsentService.scheduleIfConsented(() -> false, scheduled::incrementAndGet);

        assertFalse(accepted);
        assertEquals(0, scheduled.get());
    }

    @Test
    void previousPolicyVersionDoesNotScheduleNetworkWork() {
        AtomicInteger scheduled = new AtomicInteger();

        boolean accepted = PrivacyConsentService.scheduleIfConsented(
                () -> PrivacyConsentService.hasCurrentConsent("2026-01-01"),
                scheduled::incrementAndGet
        );

        assertFalse(accepted);
        assertEquals(0, scheduled.get());
    }

    @Test
    void revocationBeforeSchedulingDoesNotScheduleNetworkWork() {
        AtomicReference<String> storedVersion = new AtomicReference<>(PrivacyConsentService.CURRENT_POLICY_VERSION);
        storedVersion.set("");
        AtomicInteger scheduled = new AtomicInteger();

        boolean accepted = PrivacyConsentService.scheduleIfConsented(
                () -> PrivacyConsentService.hasCurrentConsent(storedVersion.get()),
                scheduled::incrementAndGet
        );

        assertFalse(accepted);
        assertEquals(0, scheduled.get());
    }

    @Test
    void currentConsentSchedulesNetworkWorkOnce() {
        AtomicInteger scheduled = new AtomicInteger();

        boolean accepted = PrivacyConsentService.scheduleIfConsented(
                () -> PrivacyConsentService.hasCurrentConsent(PrivacyConsentService.CURRENT_POLICY_VERSION),
                scheduled::incrementAndGet
        );

        assertTrue(accepted);
        assertEquals(1, scheduled.get());
    }
}
