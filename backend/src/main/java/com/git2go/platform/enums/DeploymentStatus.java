package com.git2go.platform.enums;

public enum DeploymentStatus {
    QUEUED,      // Deploy request aaya, queue me hai
    CLONING,     // Git repo clone ho raha hai
    BUILDING,    // Docker image build ho rahi hai
    DEPLOYING,   // Container start ho raha hai
    RUNNING,     // App live hai
    FAILED,      // Kahi pe fail ho gaya
    STOPPED      // User ne manually stop kiya
}
