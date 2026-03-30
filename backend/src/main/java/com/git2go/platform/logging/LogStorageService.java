package com.git2go.platform.logging;

import java.util.List;
import java.util.UUID;

/**
 * Log Storage Interface — Strategy Pattern for log persistence.
 *
 * Abhi: FileLogStorageService (local file system)
 * Future: S3LogStorageService (AWS S3), GCSLogStorageService (Google Cloud)
 *
 * Interview: "LogStorageService interface define kiya hai — abhi file system pe
 * logs store hote hain, future me S3 pe switch karna ho toh sirf ek implementation
 * swap karni padegi. Service layer ko pata nahi chalta ki logs kahan stored hain —
 * ye Dependency Inversion Principle hai (SOLID ka D)."
 */
public interface LogStorageService {

    /**
     * Log line append karo — build ke time har step pe call hoga
     */
    void appendLog(UUID deploymentId, String level, String message);

    /**
     * Saare logs read karo — user dashboard pe dikhane ke liye
     */
    List<LogEntry> readLogs(UUID deploymentId);

    /**
     * Last N lines read karo — preview/tail ke liye
     */
    List<LogEntry> readLastNLines(UUID deploymentId, int lines);

    /**
     * Logs delete karo — cleanup ke time
     */
    void deleteLogs(UUID deploymentId);

    /**
     * Check karo logs exist karte hain ya nahi
     */
    boolean logsExist(UUID deploymentId);
}
