package com.git2go.platform.orchestrator;

/**
 * Container Orchestrator Interface — Strategy Pattern
 *
 * Ye interface define karta hai ki container orchestration ke liye
 * kaunse operations available hone chahiye. Implementation chahe
 * Docker ho ya Kubernetes — service layer ko farak nahi padta.
 *
 * Abhi: DockerOrchestrator implements this
 * Future: K8sOrchestrator implements this (zero service layer changes)
 *
 * Interview: "Strategy Pattern use kiya hai SOLID principles follow karte hue.
 * ContainerOrchestrator interface hai, DockerOrchestrator concrete implementation.
 * Service layer sirf interface se baat karti hai — isse runtime pe bhi
 * implementation swap kar sakte hain (e.g., based on config). Open/Closed
 * Principle — naya orchestrator add karne ke liye existing code modify
 * nahi karna padta."
 */
public interface ContainerOrchestrator {

    /**
     * Docker image build karo source code se
     * @return image ID
     */
    String buildImage(BuildRequest request);

    /**
     * Built image se container run karo
     * @return container ID
     */
    String runContainer(RunContainerRequest request);

    /**
     * Running container stop karo
     */
    void stopContainer(String containerId);

    /**
     * Stopped container restart karo
     */
    void restartContainer(String containerId);

    /**
     * Container permanently remove karo
     */
    void removeContainer(String containerId);

    /**
     * Container ka current status check karo
     */
    ContainerStatus getContainerStatus(String containerId);

    /**
     * Container ke logs fetch karo
     */
    String getContainerLogs(String containerId, int tailLines);

    /**
     * Container ka resource usage (CPU, Memory)
     */
    ContainerStats getContainerStats(String containerId);
}
