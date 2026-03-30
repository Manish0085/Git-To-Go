package com.git2go.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class BuildLogResponse {
    private String message;
    private String level;
    private LocalDateTime timestamp;
}
