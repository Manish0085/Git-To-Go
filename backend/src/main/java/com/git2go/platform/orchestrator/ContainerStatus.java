package com.git2go.platform.orchestrator;

public enum ContainerStatus {
    RUNNING,
    STOPPED,
    EXITED,     // container crashed / exited on its own
    NOT_FOUND   // container exist nahi karta
}
