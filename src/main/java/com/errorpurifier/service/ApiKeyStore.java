package com.errorpurifier.service;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;

import java.util.Optional;

/** Keeps provider API keys in the IDE credential store, never in project files or the backend. */
public final class ApiKeyStore {
    private static final String SERVICE_PREFIX = "AI Error Purifier/";

    private ApiKeyStore() {
    }

    public static Optional<String> get(LlmProvider provider) {
        Credentials credentials = PasswordSafe.getInstance().get(attributes(provider));
        return credentials == null ? Optional.empty() : Optional.ofNullable(credentials.getPasswordAsString());
    }

    public static void save(LlmProvider provider, String apiKey) {
        PasswordSafe.getInstance().set(attributes(provider), new Credentials(provider.name(), apiKey));
    }

    private static CredentialAttributes attributes(LlmProvider provider) {
        return new CredentialAttributes(SERVICE_PREFIX + provider.name());
    }
}
