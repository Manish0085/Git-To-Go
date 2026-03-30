package com.git2go.platform.logging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class LogEntry {
    private String level;      // INFO, ERROR, WARN
    private String message;
    private LocalDateTime timestamp;
}
