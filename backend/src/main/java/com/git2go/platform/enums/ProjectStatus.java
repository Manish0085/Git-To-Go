package com.git2go.platform.enums;

public enum ProjectStatus {
    CREATED,     // Project bana but deploy nahi hua
    BUILDING,    // Docker image build ho rahi hai
    DEPLOYING,   // Container start ho raha hai
    RUNNING,     // App live hai
    STOPPED,     // User ne manually stop kiya
    FAILED       // Build ya deployment fail ho gaya
}
