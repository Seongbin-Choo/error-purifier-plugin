package com.errorpurifier.util;

import com.intellij.openapi.application.PathManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/** Persists only the server-issued installation UUID in the IDE configuration directory. */
public final class DeviceAuthManager {

    private static final Path DEVICE_FILE = Path.of(PathManager.getConfigPath(), "error-purifier", "device-uuid");
    private static volatile String cachedUuid;

    private DeviceAuthManager() {
    }

    public static Optional<String> readDeviceId() {
        if (cachedUuid != null) {
            return Optional.of(cachedUuid);
        }
        try {
            if (!Files.exists(DEVICE_FILE)) {
                return Optional.empty();
            }
            String value = Files.readString(DEVICE_FILE, StandardCharsets.UTF_8).trim();
            UUID.fromString(value);
            cachedUuid = value;
            return Optional.of(value);
        } catch (IllegalArgumentException | IOException exception) {
            deleteInvalidFile();
            return Optional.empty();
        }
    }

    public static synchronized void saveDeviceId(String deviceId) throws IOException {
        UUID.fromString(deviceId);
        Files.createDirectories(DEVICE_FILE.getParent());
        Path temporaryFile = Files.createTempFile(DEVICE_FILE.getParent(), "device-uuid-", ".tmp");
        Files.writeString(temporaryFile, deviceId, StandardCharsets.UTF_8);
        Files.move(temporaryFile, DEVICE_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        cachedUuid = deviceId;
    }

    private static void deleteInvalidFile() {
        try {
            Files.deleteIfExists(DEVICE_FILE);
        } catch (IOException ignored) {
            // The next sync still requests a server-issued UUID without depending on this file.
        }
        cachedUuid = null;
    }
}
