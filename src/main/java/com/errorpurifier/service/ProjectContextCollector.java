package com.errorpurifier.service;

import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class ProjectContextCollector {

    private static final List<String> CANDIDATES = List.of(
            "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "pom.xml",
            "src/main/resources/application.yml", "src/main/resources/application.yaml", "src/main/resources/application.properties"
    );
    private static final Pattern SAFE_BUILD_LINE = Pattern.compile(
            "(?i).*(org\\.springframework\\.boot|org\\.jetbrains\\.kotlin|languageVersion|sourceCompatibility|targetCompatibility|JavaVersion|java\\.version|spring-boot).*|^\\s*group\\s*=\\s*['\"][A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+['\"]\\s*$"
    );

    private ProjectContextCollector() {
    }

    public static Map<String, String> collectProjectFiles(Project project) {
        Map<String, String> files = new LinkedHashMap<>();
        String basePath = project.getBasePath();
        if (basePath == null) {
            return files;
        }
        Path root = Path.of(basePath);
        for (String relativePath : CANDIDATES) {
            Path candidate = root.resolve(relativePath);
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            files.put(relativePath, safeMetadata(candidate, relativePath));
        }
        return files;
    }

    public static Map<String, String> environmentTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("ide", "intellij");
        tags.put("plugin", "error-purifier");
        return tags;
    }

    private static String safeMetadata(Path path, String relativePath) {
        try {
            String lowerPath = relativePath.toLowerCase(Locale.ROOT);
            if (lowerPath.endsWith(".yml") || lowerPath.endsWith(".yaml") || lowerPath.endsWith(".properties")) {
                return "spring configuration file present";
            }
            return Files.readAllLines(path).stream()
                    .filter(line -> SAFE_BUILD_LINE.matcher(line).matches())
                    .limit(50)
                    .reduce("", (left, right) -> left + right + "\n");
        } catch (IOException exception) {
            return "metadata unavailable";
        }
    }
}
