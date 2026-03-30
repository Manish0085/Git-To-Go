package com.git2go.platform.service;

import com.git2go.platform.enums.ProjectLanguage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

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

    private boolean fileExists(Path dir, String filename) {
        return Files.exists(dir.resolve(filename));
    }
}
