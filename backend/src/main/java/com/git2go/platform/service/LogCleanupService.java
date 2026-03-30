package com.git2go.platform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

/**
 * Log Cleanup Service — purane log files automatically delete karta hai.
 *
 * @Scheduled cron job — har raat 2 AM pe chalta hai.
 * 7 din se purani log files delete karta hai.
 *
 * Interview: "Scheduled cleanup job implement ki hai @Scheduled annotation se.
 * Cron expression use ki hai — har raat 2 AM pe chalta hai. Files.walk() se
 * log directory traverse karta hoon, lastModified time check karta hoon,
 * retention period se purani files delete karta hoon. Ye disk space management
 * automate karta hai."
 */
@Slf4j
@Service
public class LogCleanupService {

    @Value("${app.logs.base-dir:/tmp/git2go/logs}")
    private String logsBaseDir;

    @Value("${app.logs.retention-days:7}")
    private int retentionDays;

    /**
     * Har raat 2 AM pe chalta hai — purane log files delete
     * Cron: second minute hour day month weekday
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldLogs() {
        log.info("Starting log cleanup — deleting files older than {} days", retentionDays);

        Path logsDir = Path.of(logsBaseDir);
        if (!Files.exists(logsDir)) {
            log.debug("Logs directory does not exist: {}", logsBaseDir);
            return;
        }

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deletedCount = 0;

        try (Stream<Path> files = Files.walk(logsDir)) {
            var oldFiles = files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".log"))
                    .filter(file -> {
                        try {
                            return Files.getLastModifiedTime(file).toInstant().isBefore(cutoff);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .toList();

            for (Path file : oldFiles) {
                try {
                    Files.delete(file);
                    deletedCount++;
                } catch (IOException e) {
                    log.warn("Failed to delete old log file {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Log cleanup failed: {}", e.getMessage());
        }

        log.info("Log cleanup complete — deleted {} old log files", deletedCount);
    }
}
