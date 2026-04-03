package com.git2go.platform.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class CreateProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(max = 100, message = "Project name must be under 100 characters")
    private String name;

    @NotBlank(message = "Repository URL is required")
    @Pattern(regexp = "^https://.*", message = "Only HTTPS repository URLs are allowed")
    private String repoUrl;

    // Default "main" — frontend se nahi bhi aaye toh chalega
    @Pattern(regexp = "^[\\w./-]+$", message = "Invalid branch name")
    private String branch = "main";

    // Port is optional — auto-detected during build if not provided
    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private Integer port;

    // Environment variables — key-value pairs (optional)
    // e.g., {"DATABASE_URL": "postgres://...", "API_KEY": "abc123"}
    private Map<String, String> envVariables;
}
