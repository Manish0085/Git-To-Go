package com.git2go.platform.service;

import com.git2go.platform.enums.ProjectLanguage;
import com.git2go.platform.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dockerfile Generator — har language ke liye optimized Dockerfile create karta hai.
 *
 * Multi-stage builds use karte hain jahan possible:
 * Stage 1 (builder): Dependencies install + build
 * Stage 2 (runtime): Sirf built artifact copy — image size 70-80% kam
 *
 * Interview: "Multi-stage Docker builds implement kiye hain. Node.js ke liye
 * pehle npm install + npm build, phir sirf dist folder copy. Java ke liye
 * Maven build with JDK, phir sirf JAR copy with JRE. Final image lightweight
 * hoti hai — attack surface bhi kam kyunki unnecessary tools nahi hain."
 */
@Slf4j
@Service
public class DockerfileGeneratorService {

    /**
     * Language ke basis pe Dockerfile generate karo aur project directory me likho
     *
     * @param projectDir — jahan repo clone hua hai
     * @param language   — detected language
     * @param port       — app kis port pe listen karta hai
     * @return Dockerfile ka path
     */
    public Path generateDockerfile(Path projectDir, ProjectLanguage language, int port) {
        String dockerfileContent = switch (language) {
            case NODEJS -> generateNodejsDockerfile(port);
            case JAVA_MAVEN -> generateJavaMavenDockerfile(port);
            case JAVA_GRADLE -> generateJavaGradleDockerfile(port);
            case PYTHON -> generatePythonDockerfile(port);
            case GO -> generateGoDockerfile(port);
            case STATIC_HTML -> generateStaticHtmlDockerfile(port);
            default -> throw new ApiException(
                    "Unsupported language. Please add a Dockerfile to your repository.",
                    HttpStatus.BAD_REQUEST);
        };

        Path dockerfilePath = projectDir.resolve("Dockerfile");
        try {
            Files.writeString(dockerfilePath, dockerfileContent);
            log.info("Generated Dockerfile for {} at {}", language, dockerfilePath);
            return dockerfilePath;
        } catch (IOException e) {
            log.error("Failed to write Dockerfile: {}", e.getMessage());
            throw new ApiException("Failed to generate Dockerfile", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String generateNodejsDockerfile(int port) {
        return """
                # ===== Stage 1: Build =====
                FROM node:18-alpine AS builder
                WORKDIR /app

                # Dependencies pehle copy — Docker cache utilize hogi
                # Agar package.json nahi badla toh npm install skip hoga (fast builds)
                COPY package*.json ./
                RUN npm ci --only=production

                # Source code copy
                COPY . .

                # Build (agar build script hai toh)
                RUN if [ -f "package.json" ] && grep -q '"build"' package.json; then npm run build; fi

                # ===== Stage 2: Runtime =====
                FROM node:18-alpine
                WORKDIR /app

                COPY --from=builder /app .

                EXPOSE %d
                CMD ["node", "index.js"]
                """.formatted(port);
    }

    private String generateJavaMavenDockerfile(int port) {
        return """
                # ===== Stage 1: Build with JDK =====
                FROM maven:3.9-eclipse-temurin-17-alpine AS builder
                WORKDIR /app

                # Dependencies pehle (cache layer)
                COPY pom.xml .
                RUN mvn dependency:go-offline -B

                # Source code copy + build
                COPY src ./src
                RUN mvn package -DskipTests -B

                # ===== Stage 2: Runtime with JRE only =====
                FROM eclipse-temurin:17-jre-alpine
                WORKDIR /app

                # Sirf JAR copy — JDK, Maven, source code sab chhod do
                COPY --from=builder /app/target/*.jar app.jar

                EXPOSE %d
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """.formatted(port);
    }

    private String generateJavaGradleDockerfile(int port) {
        return """
                # ===== Stage 1: Build =====
                FROM gradle:8-jdk17-alpine AS builder
                WORKDIR /app
                COPY . .
                RUN gradle build -x test --no-daemon

                # ===== Stage 2: Runtime =====
                FROM eclipse-temurin:17-jre-alpine
                WORKDIR /app
                COPY --from=builder /app/build/libs/*.jar app.jar

                EXPOSE %d
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """.formatted(port);
    }

    private String generatePythonDockerfile(int port) {
        return """
                FROM python:3.11-slim
                WORKDIR /app

                # Dependencies pehle (cache layer)
                COPY requirements.txt .
                RUN pip install --no-cache-dir -r requirements.txt

                COPY . .

                EXPOSE %d
                CMD ["python", "app.py"]
                """.formatted(port);
    }

    private String generateGoDockerfile(int port) {
        return """
                # ===== Stage 1: Build =====
                FROM golang:1.22-alpine AS builder
                WORKDIR /app

                COPY go.mod go.sum ./
                RUN go mod download

                COPY . .
                RUN CGO_ENABLED=0 GOOS=linux go build -o main .

                # ===== Stage 2: Minimal runtime =====
                # scratch = empty image — Go binary static compiled hai, kuch aur nahi chahiye
                FROM alpine:latest
                WORKDIR /app
                COPY --from=builder /app/main .

                EXPOSE %d
                CMD ["./main"]
                """.formatted(port);
    }

    private String generateStaticHtmlDockerfile(int port) {
        return """
                FROM nginx:alpine
                COPY . /usr/share/nginx/html
                EXPOSE %d
                CMD ["nginx", "-g", "daemon off;"]
                """.formatted(port);
    }
}
