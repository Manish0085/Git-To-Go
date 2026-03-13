package com.deployment.Git2Go.enums;

public enum BuildStatus {
    QUEUED,
    CLONING,
    DETECTING,
    BUILDING,
    PUSHING,
    DEPLOYING,
    LIVE,
    FAILED,
    ROLLED_BACK
}