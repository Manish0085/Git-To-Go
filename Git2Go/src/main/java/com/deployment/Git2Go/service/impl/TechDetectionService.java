package com.deployment.Git2Go.service.impl;

import com.deployment.Git2Go.enums.TechStack;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class TechDetectionService {

    public TechStack detect(Path repoPath) {
        log.info("Detecting tech stack at: {}", repoPath);

        // Java / Spring Boot
        if (exists(repoPath, "pom.xml")) {
            if (containsText(repoPath, "pom.xml", "spring-boot")) {
                log.info("Detected: SPRING_BOOT");
                return TechStack.SPRING_BOOT;
            }
            log.info("Detected: JAVA");
            return TechStack.JAVA;
        }

        if (exists(repoPath, "build.gradle")) {
            if (containsText(repoPath, "build.gradle", "spring-boot")) {
                log.info("Detected: SPRING_BOOT");
                return TechStack.SPRING_BOOT;
            }
            log.info("Detected: JAVA");
            return TechStack.JAVA;
        }

        // Node.js
        if (exists(repoPath, "package.json")) {
            if (exists(repoPath, "next.config.js") ||
                exists(repoPath, "next.config.ts")) {
                log.info("Detected: NEXTJS");
                return TechStack.NEXTJS;
            }
            log.info("Detected: NODEJS");
            return TechStack.NODEJS;
        }

        // Python
        if (exists(repoPath, "requirements.txt")) {
            if (containsText(repoPath, "requirements.txt", "fastapi")) {
                log.info("Detected: FASTAPI");
                return TechStack.FASTAPI;
            }
            if (containsText(repoPath, "requirements.txt", "django")) {
                log.info("Detected: DJANGO");
                return TechStack.DJANGO;
            }
            log.info("Detected: PYTHON");
            return TechStack.PYTHON;
        }

        if (exists(repoPath, "pyproject.toml")) {
            log.info("Detected: PYTHON");
            return TechStack.PYTHON;
        }

        // Static site
        if (exists(repoPath, "index.html")) {
            log.info("Detected: STATIC");
            return TechStack.STATIC;
        }

        log.warn("Could not detect tech stack");
        return TechStack.UNKNOWN;
    }

    public String generateDockerfile(TechStack stack) {
        return switch (stack) {
            case SPRING_BOOT -> """
                FROM maven:3.9-eclipse-temurin-17 AS build
                WORKDIR /app
                COPY . .
                RUN mvn clean package -DskipTests
                FROM eclipse-temurin:17-jre-alpine
                WORKDIR /app
                COPY --from=build /app/target/*.jar app.jar
                EXPOSE 8080
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

            case JAVA -> """
                FROM maven:3.9-eclipse-temurin-17 AS build
                WORKDIR /app
                COPY . .
                RUN mvn clean package -DskipTests
                FROM eclipse-temurin:17-jre-alpine
                WORKDIR /app
                COPY --from=build /app/target/*.jar app.jar
                EXPOSE 8080
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

            case NODEJS -> """
                FROM node:20-alpine
                WORKDIR /app
                COPY package*.json ./
                RUN npm ci --only=production
                COPY . .
                EXPOSE 3000
                CMD ["node", "index.js"]
                """;

            case NEXTJS -> """
                FROM node:20-alpine AS build
                WORKDIR /app
                COPY package*.json ./
                RUN npm ci
                COPY . .
                RUN npm run build
                FROM node:20-alpine
                WORKDIR /app
                COPY --from=build /app/.next ./.next
                COPY --from=build /app/node_modules ./node_modules
                COPY --from=build /app/package.json ./package.json
                EXPOSE 3000
                CMD ["npm", "start"]
                """;

            case FASTAPI -> """
                FROM python:3.11-slim
                WORKDIR /app
                COPY requirements.txt .
                RUN pip install --no-cache-dir -r requirements.txt
                COPY . .
                EXPOSE 8000
                CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
                """;

            case DJANGO -> """
                FROM python:3.11-slim
                WORKDIR /app
                COPY requirements.txt .
                RUN pip install --no-cache-dir -r requirements.txt
                COPY . .
                EXPOSE 8000
                CMD ["python", "manage.py", "runserver", "0.0.0.0:8000"]
                """;

            case PYTHON -> """
                FROM python:3.11-slim
                WORKDIR /app
                COPY requirements.txt .
                RUN pip install --no-cache-dir -r requirements.txt
                COPY . .
                EXPOSE 8000
                CMD ["python", "app.py"]
                """;

            case STATIC -> """
                FROM nginx:alpine
                COPY . /usr/share/nginx/html
                EXPOSE 80
                CMD ["nginx", "-g", "daemon off;"]
                """;

            default -> """
                FROM ubuntu:22.04
                WORKDIR /app
                COPY . .
                EXPOSE 8080
                CMD ["echo", "Unknown tech stack — please add a Dockerfile"]
                """;
        };
    }

    // ── helpers ──────────────────────────────────────────────

    private boolean exists(Path root, String filename) {
        return Files.exists(root.resolve(filename));
    }

    private boolean containsText(Path root, String filename, String text) {
        try {
            String content = Files.readString(root.resolve(filename));
            return content.toLowerCase().contains(text.toLowerCase());
        } catch (IOException e) {
            return false;
        }
    }
}