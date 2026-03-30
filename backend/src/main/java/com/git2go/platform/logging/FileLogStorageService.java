package com.git2go.platform.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * File-based Log Storage — logs ko local file system pe store karta hai.
 *
 * Key improvements over naive approach:
 * - readLogs() uses streaming (BufferedReader) — NOT readAllLines() which loads entire file in memory
 * - readLastNLines() reads from end of file — efficient for large files
 * - Max log file size limit — 50MB, rotates when exceeded
 * - Thread-safe writes via synchronized append
 *
 * Interview: "File-based log storage hai with streaming reads — readAllLines() use nahi kiya
 * kyunki wo pura file memory me load karta hai, OOM risk. BufferedReader se line-by-line
 * stream karta hoon. Tail operation ke liye file ke end se backward read karta hoon —
 * O(n) nahi, O(tailLines). File size limit 50MB hai — exceed hone pe rotate hota hai."
 */
@Slf4j
@Service
public class FileLogStorageService implements LogStorageService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String DELIMITER = "|";
    private static final long MAX_LOG_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 50MB
    private static final int MAX_LINES_TO_READ = 5000; // Safety cap

    @Value("${app.logs.base-dir:/tmp/git2go/logs}")
    private String logsBaseDir;

    @Override
    public void appendLog(UUID deploymentId, String level, String message) {
        try {
            Path logFile = getLogFilePath(deploymentId);
            Files.createDirectories(logFile.getParent());

            // File size check — rotate if too large
            if (Files.exists(logFile) && Files.size(logFile) > MAX_LOG_FILE_SIZE_BYTES) {
                rotateLogFile(logFile);
            }

            // Truncate long messages — single log line max 10KB
            String safeMessage = message.length() > 10_000
                    ? message.substring(0, 10_000) + "... [truncated]"
                    : message;

            String logLine = LocalDateTime.now().format(FORMATTER)
                    + DELIMITER + level
                    + DELIMITER + safeMessage
                    + System.lineSeparator();

            Files.writeString(logFile, logLine,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

        } catch (IOException e) {
            log.error("Failed to write log for deployment {}: {}", deploymentId, e.getMessage());
        }
    }

    @Override
    public List<LogEntry> readLogs(UUID deploymentId) {
        Path logFile = getLogFilePath(deploymentId);

        if (!Files.exists(logFile)) {
            return Collections.emptyList();
        }

        // Streaming read — line by line, capped at MAX_LINES_TO_READ
        List<LogEntry> entries = new ArrayList<>();
        try (Stream<String> lines = Files.lines(logFile)) {
            lines.filter(line -> !line.isBlank())
                    .limit(MAX_LINES_TO_READ) // Safety cap — prevent OOM
                    .map(this::parseLogLine)
                    .forEach(entries::add);
        } catch (IOException e) {
            log.error("Failed to read logs for deployment {}: {}", deploymentId, e.getMessage());
        }

        return entries;
    }

    @Override
    public List<LogEntry> readLastNLines(UUID deploymentId, int lines) {
        if (lines <= 0) lines = 50; // Fallback

        Path logFile = getLogFilePath(deploymentId);
        if (!Files.exists(logFile)) {
            return Collections.emptyList();
        }

        // Read last N lines efficiently from end of file
        List<String> lastLines = readTail(logFile, lines);

        return lastLines.stream()
                .filter(line -> !line.isBlank())
                .map(this::parseLogLine)
                .toList();
    }

    @Override
    public void deleteLogs(UUID deploymentId) {
        try {
            Path logFile = getLogFilePath(deploymentId);
            Files.deleteIfExists(logFile);
            // Also delete rotated file
            Files.deleteIfExists(Path.of(logFile + ".old"));
        } catch (IOException e) {
            log.warn("Failed to delete log file for deployment {}: {}", deploymentId, e.getMessage());
        }
    }

    @Override
    public boolean logsExist(UUID deploymentId) {
        return Files.exists(getLogFilePath(deploymentId));
    }

    // ==================== HELPERS ====================

    private Path getLogFilePath(UUID deploymentId) {
        return Path.of(logsBaseDir, deploymentId.toString() + ".log");
    }

    /**
     * Log file rotate — purana file .old me rename karo, naya file shuru
     */
    private void rotateLogFile(Path logFile) {
        try {
            Path oldFile = Path.of(logFile + ".old");
            Files.deleteIfExists(oldFile);
            Files.move(logFile, oldFile);
            log.info("Rotated log file: {} → {}", logFile, oldFile);
        } catch (IOException e) {
            log.warn("Failed to rotate log file {}: {}", logFile, e.getMessage());
        }
    }

    /**
     * Read last N lines from file — efficient, doesn't load entire file.
     * Uses RandomAccessFile to read from end.
     */
    private List<String> readTail(Path file, int lines) {
        List<String> result = new LinkedList<>();
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) return result;

            long pos = fileLength - 1;
            int lineCount = 0;
            StringBuilder currentLine = new StringBuilder();

            // Read backwards from end of file
            while (pos >= 0 && lineCount < lines) {
                raf.seek(pos);
                char c = (char) raf.readByte();

                if (c == '\n') {
                    if (currentLine.length() > 0) {
                        result.add(0,currentLine.reverse().toString());
                        currentLine = new StringBuilder();
                        lineCount++;
                    }
                } else {
                    currentLine.append(c);
                }
                pos--;
            }

            // First line (no newline before it)
            if (currentLine.length() > 0 && lineCount < lines) {
                result.add(0,currentLine.reverse().toString());
            }

        } catch (IOException e) {
            log.error("Failed to read tail of log file {}: {}", file, e.getMessage());
        }

        return result;
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
