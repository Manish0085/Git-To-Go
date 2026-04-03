package com.git2go.platform.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * S3-based Log Storage — logs ko AWS S3 bucket pe store karta hai.
 *
 * Activate:   app.logs.storage=s3   (in .env)
 * Deactivate: app.logs.storage=file (default — FileLogStorageService chalegi)
 *
 * Interview: "S3LogStorageService implement ki hai as an alternative to FileLogStorageService.
 * Same LogStorageService interface implement karti hai. @ConditionalOnProperty se runtime pe
 * switch hota hai — zero code change. Strategy Pattern + Dependency Inversion Principle."
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.logs.storage", havingValue = "s3")
public class S3LogStorageService implements LogStorageService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String DELIMITER = "|";
    private static final int MAX_LINES_TO_READ = 5000;

    @Value("${app.logs.s3.bucket}")
    private String bucketName;

    @Value("${app.logs.s3.region:ap-south-1}")
    private String region;

    @Value("${app.logs.s3.access-key:}")
    private String accessKey;

    @Value("${app.logs.s3.secret-key:}")
    private String secretKey;

    @Value("${app.logs.s3.prefix:logs/}")
    private String keyPrefix;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        var builder = S3Client.builder().region(Region.of(region));

        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
            ));
            log.info("S3 Log Storage: static credentials — bucket: {}", bucketName);
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
            log.info("S3 Log Storage: default credentials chain (EC2 IAM role) — bucket: {}", bucketName);
        }

        this.s3Client = builder.build();
    }

    @Override
    public void appendLog(UUID deploymentId, String level, String message) {
        try {
            String key = getKey(deploymentId);

            String safeMessage = message.length() > 10_000
                    ? message.substring(0, 10_000) + "... [truncated]"
                    : message;

            String newLine = LocalDateTime.now().format(FORMATTER)
                    + DELIMITER + level
                    + DELIMITER + safeMessage + "\n";

            // S3 mein direct append nahi hota — download + append + re-upload
            String existing = downloadContent(key);
            String updated = existing + newLine;

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType("text/plain")
                            .build(),
                    RequestBody.fromString(updated, StandardCharsets.UTF_8)
            );

        } catch (Exception e) {
            log.error("S3 appendLog failed for deployment {}: {}", deploymentId, e.getMessage());
        }
    }

    @Override
    public List<LogEntry> readLogs(UUID deploymentId) {
        try {
            String content = downloadContent(getKey(deploymentId));
            if (content.isBlank()) return Collections.emptyList();

            return content.lines()
                    .filter(line -> !line.isBlank())
                    .limit(MAX_LINES_TO_READ)
                    .map(this::parseLogLine)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("S3 readLogs failed for deployment {}: {}", deploymentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<LogEntry> readLastNLines(UUID deploymentId, int lines) {
        if (lines <= 0) lines = 50;

        try {
            String content = downloadContent(getKey(deploymentId));
            if (content.isBlank()) return Collections.emptyList();

            List<String> allLines = content.lines()
                    .filter(line -> !line.isBlank())
                    .collect(Collectors.toList());

            int fromIndex = Math.max(0, allLines.size() - lines);
            return allLines.subList(fromIndex, allLines.size())
                    .stream()
                    .map(this::parseLogLine)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("S3 readLastNLines failed for deployment {}: {}", deploymentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void deleteLogs(UUID deploymentId) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(getKey(deploymentId))
                    .build());
            log.info("Deleted S3 logs for deployment: {}", deploymentId);
        } catch (Exception e) {
            log.warn("S3 deleteLogs failed for deployment {}: {}", deploymentId, e.getMessage());
        }
    }

    @Override
    public boolean logsExist(UUID deploymentId) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(getKey(deploymentId))
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.warn("S3 logsExist check failed for deployment {}: {}", deploymentId, e.getMessage());
            return false;
        }
    }

    // ==================== HELPERS ====================

    private String getKey(UUID deploymentId) {
        return keyPrefix + deploymentId.toString() + ".log";
    }

    private String downloadContent(String key) {
        try {
            var response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }

        } catch (NoSuchKeyException e) {
            return "";
        } catch (Exception e) {
            log.error("S3 download failed for key {}: {}", key, e.getMessage());
            return "";
        }
    }

    private LogEntry parseLogLine(String line) {
        String[] parts = line.split("\\|", 3);

        if (parts.length < 3) {
            return LogEntry.builder()
                    .timestamp(LocalDateTime.now())
                    .level("INFO")
                    .message(line)
                    .build();
        }

        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(parts[0], FORMATTER);
        } catch (Exception e) {
            timestamp = LocalDateTime.now();
        }

        return LogEntry.builder()
                .timestamp(timestamp)
                .level(parts[1])
                .message(parts[2])
                .build();
    }
}
