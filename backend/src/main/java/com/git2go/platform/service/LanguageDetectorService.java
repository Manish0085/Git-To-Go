package com.git2go.platform.service;

import com.git2go.platform.enums.ProjectLanguage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Language Detector — repo me kaunsi files hain usse language detect karta hai.
 *
 * Detection order matters — pehle specific check, phir generic.
 * e.g., package.json check pehle kyunki Node.js projects me ye hamesha hota hai.
 *
 * Interview: "Heuristic-based language detection implement ki hai using marker files.
 * Same approach Heroku, Railway, Render sab use karte hain. pom.xml detect hua
 * toh Java Maven, build.gradle toh Java Gradle, package.json toh Node.js.
 * Agar repo me already Dockerfile hai toh detection skip — user ka Dockerfile
 * respect karte hain."
 */
@Slf4j
@Service
public class LanguageDetectorService {

    /**
     * Detect project language/framework based on marker files
     *
     * @param projectDir — cloned repo ka path
     * @return detected language
     */
    public ProjectLanguage detectLanguage(Path projectDir) {
        log.info("Detecting language for project at: {}", projectDir);

        // Check karo ki kaunsi marker file exist karti hai
        if (fileExists(projectDir, "package.json")) {
            log.info("Detected: Node.js (package.json found)");
            return ProjectLanguage.NODEJS;
        }

        if (fileExists(projectDir, "pom.xml")) {
            log.info("Detected: Java Maven (pom.xml found)");
            return ProjectLanguage.JAVA_MAVEN;
        }

        if (fileExists(projectDir, "build.gradle") || fileExists(projectDir, "build.gradle.kts")) {
            log.info("Detected: Java Gradle (build.gradle found)");
            return ProjectLanguage.JAVA_GRADLE;
        }

        if (fileExists(projectDir, "requirements.txt") || fileExists(projectDir, "Pipfile") || fileExists(projectDir, "pyproject.toml")) {
            log.info("Detected: Python (requirements.txt/Pipfile/pyproject.toml found)");
            return ProjectLanguage.PYTHON;
        }

        if (fileExists(projectDir, "go.mod")) {
            log.info("Detected: Go (go.mod found)");
            return ProjectLanguage.GO;
        }

        if (fileExists(projectDir, "index.html")) {
            log.info("Detected: Static HTML (index.html found)");
            return ProjectLanguage.STATIC_HTML;
        }

        log.warn("Could not detect language for project at: {}", projectDir);
        return ProjectLanguage.UNKNOWN;
    }

    /**
     * Check karo ki repo me already Dockerfile hai ya nahi
     * Agar hai toh hum apna generate nahi karenge — user ka respect karo
     */
    public boolean hasDockerfile(Path projectDir) {
        return fileExists(projectDir, "Dockerfile");
    }

    /**
     * Auto-detect port from project config files.
     * Checks language-specific config files for port settings.
     * Falls back to standard defaults per language.
     */
    public int detectPort(Path projectDir, ProjectLanguage language) {
        // Priority 1: Config file (most reliable — actual app config)
        int detectedPort = tryDetectPortFromConfig(projectDir, language);
        if (detectedPort > 0) {
            log.info("Port detected from config: {}", detectedPort);
            return detectedPort;
        }

        // Priority 2: Language default (more reliable than Dockerfile EXPOSE)
        // Dockerfile EXPOSE is often wrong/outdated — skip it when language is known
        if (language != ProjectLanguage.UNKNOWN) {
            int defaultPort = getLanguageDefaultPort(language);
            log.info("Using default port for {}: {}", language, defaultPort);
            return defaultPort;
        }

        // Priority 3: Dockerfile EXPOSE (only when language is UNKNOWN)
        if (hasDockerfile(projectDir)) {
            int dockerPort = tryDetectPortFromDockerfile(projectDir);
            if (dockerPort > 0) {
                log.info("Port detected from Dockerfile EXPOSE: {}", dockerPort);
                return dockerPort;
            }
        }

        return 8080; // Ultimate fallback
    }

    private int getLanguageDefaultPort(ProjectLanguage language) {
        return switch (language) {
            case NODEJS -> 3000;
            case JAVA_MAVEN, JAVA_GRADLE -> 8080;
            case PYTHON -> 5000;
            case GO -> 8080;
            case STATIC_HTML -> 80;
            default -> 8080;
        };
    }

    private int tryDetectPortFromConfig(Path projectDir, ProjectLanguage language) {
        try {
            return switch (language) {
                case JAVA_MAVEN, JAVA_GRADLE -> detectSpringBootPort(projectDir);
                case NODEJS -> detectNodePort(projectDir);
                case PYTHON -> detectPythonPort(projectDir);
                default -> 0;
            };
        } catch (Exception e) {
            log.debug("Could not detect port from config: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Spring Boot — application.properties ya application.yaml se server.port padho
     */
    private int detectSpringBootPort(Path projectDir) {
        Path props = projectDir.resolve("src/main/resources/application.properties");
        if (Files.exists(props)) {
            int port = extractPort(props, Pattern.compile("server\\.port\\s*=\\s*(\\d+)"));
            if (port > 0) return port;
        }
        for (String name : new String[]{"application.yaml", "application.yml"}) {
            Path yaml = projectDir.resolve("src/main/resources/" + name);
            if (Files.exists(yaml)) {
                int port = extractPort(yaml, Pattern.compile("port:\\s*(\\d+)"));
                if (port > 0) return port;
            }
        }
        return 0;
    }

    private int detectNodePort(Path projectDir) {
        Path packageJson = projectDir.resolve("package.json");
        if (Files.exists(packageJson)) {
            int port = extractPort(packageJson, Pattern.compile("PORT[\"']?\\s*[:=]\\s*[\"']?(\\d+)"));
            if (port > 0) return port;
        }
        return 0;
    }

    private int detectPythonPort(Path projectDir) {
        for (String name : new String[]{"app.py", "main.py", "manage.py"}) {
            Path file = projectDir.resolve(name);
            if (Files.exists(file)) {
                int port = extractPort(file, Pattern.compile("port\\s*=\\s*(\\d+)"));
                if (port > 0) return port;
            }
        }
        return 0;
    }

    private int tryDetectPortFromDockerfile(Path projectDir) {
        return extractPort(projectDir.resolve("Dockerfile"), Pattern.compile("EXPOSE\\s+(\\d+)"));
    }

    private int extractPort(Path file, Pattern pattern) {
        try {
            String content = Files.readString(file);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                int port = Integer.parseInt(matcher.group(1));
                if (port >= 1 && port <= 65535) return port;
            }
        } catch (Exception e) {
            log.debug("Failed to read {}: {}", file, e.getMessage());
        }
        return 0;
    }

    private boolean fileExists(Path dir, String filename) {
        return Files.exists(dir.resolve(filename));
    }
}
