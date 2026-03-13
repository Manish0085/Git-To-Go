package com.deployment.Git2Go.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EcsDeployService {

    private final EcsClient ecsClient;

    @Value("${aws.ecs.cluster}")
    private String ecsCluster;

    @Value("${aws.ecs.execution-role-arn}")
    private String executionRoleArn;

    @Value("${aws.region}")
    private String awsRegion;

    // Registers new task definition and updates ECS service
    // Returns new task definition ARN
    public String deploy(String projectId,
                          String imageUri,
                          String deploymentId,
                          LogStreamingService logService) throws Exception {

        String serviceName = "git2go-" + projectId.substring(0, 8);
        String family      = "git2go-task-" + projectId.substring(0, 8);

        logService.pushLog(deploymentId,
            "📋 Registering ECS task definition...");

        // 1. Register new task definition with new image
        RegisterTaskDefinitionResponse taskDefResponse =
            ecsClient.registerTaskDefinition(
                RegisterTaskDefinitionRequest.builder()
                    .family(family)
                    .networkMode(NetworkMode.AWSVPC)
                    .requiresCompatibilities(Compatibility.FARGATE)
                    .cpu("256")
                    .memory("512")
                    .executionRoleArn(executionRoleArn)
                    .containerDefinitions(
                        ContainerDefinition.builder()
                            .name("app")
                            .image(imageUri)
                            .essential(true)
                            .portMappings(
                                PortMapping.builder()
                                    .containerPort(8080)
                                    .protocol(TransportProtocol.TCP)
                                    .build()
                            )
                            .logConfiguration(
                                LogConfiguration.builder()
                                    .logDriver(LogDriver.AWSLOGS)
                                    .options(java.util.Map.of(
                                        "awslogs-group",
                                        "/ecs/git2go-" + projectId.substring(0, 8),
                                        "awslogs-region", awsRegion,
                                        "awslogs-stream-prefix", "ecs"
                                    ))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            );

        String newTaskDefArn = taskDefResponse
            .taskDefinition().taskDefinitionArn();

        logService.pushLog(deploymentId,
            "✅ Task definition registered: " + newTaskDefArn);

        // 2. Check if service exists — create or update
        boolean serviceExists = serviceExists(serviceName);

        if (!serviceExists) {
            logService.pushLog(deploymentId,
                "🆕 Creating ECS service for first deploy...");
            createService(serviceName, newTaskDefArn, logService, deploymentId);
        } else {
            logService.pushLog(deploymentId,
                "🔄 Updating ECS service...");
            ecsClient.updateService(
                UpdateServiceRequest.builder()
                    .cluster(ecsCluster)
                    .service(serviceName)
                    .taskDefinition(newTaskDefArn)
                    .forceNewDeployment(true)
                    .build()
            );
        }

        logService.pushLog(deploymentId,
            "⏳ Waiting for service to stabilize...");

        return newTaskDefArn;
    }

    // Polls ECS until service is stable or times out
    // Returns true if healthy, false if failed
    public boolean waitForStability(String projectId,
                                     String deploymentId,
                                     LogStreamingService logService)
            throws InterruptedException {

        String serviceName = "git2go-" + projectId.substring(0, 8);
        int maxAttempts = 30; // 30 x 10s = 5 minutes timeout

        for (int i = 0; i < maxAttempts; i++) {
            Thread.sleep(10_000);

            DescribeServicesResponse response = ecsClient.describeServices(
                DescribeServicesRequest.builder()
                    .cluster(ecsCluster)
                    .services(serviceName)
                    .build()
            );

            if (response.services().isEmpty()) continue;

            software.amazon.awssdk.services.ecs.model.Service service =
                response.services().get(0);

            int running = service.runningCount();
            int desired = service.desiredCount();
            int pending = service.pendingCount();

            logService.pushLog(deploymentId,
                String.format("🔍 ECS status — running: %d, desired: %d, pending: %d",
                    running, desired, pending));

            // Check for deployment failures
            List<ServiceEvent> events = service.events();
            if (!events.isEmpty()) {
                String latestEvent = events.get(0).message();
                logService.pushLog(deploymentId, "📢 " + latestEvent);

                if (latestEvent.contains("unable") ||
                    latestEvent.contains("failed") ||
                    latestEvent.contains("stopped")) {
                    logService.pushLog(deploymentId,
                        "❌ ECS deployment failed");
                    return false;
                }
            }

            if (running == desired && pending == 0 && desired > 0) {
                logService.pushLog(deploymentId,
                    "✅ Service is stable and healthy!");
                return true;
            }
        }

        logService.pushLog(deploymentId,
            "⏰ Timed out waiting for ECS stability");
        return false;
    }

    // ⭐ Auto rollback — reverts to previous task definition
    public void rollback(String projectId,
                          String previousTaskDefArn,
                          String deploymentId,
                          LogStreamingService logService) {

        String serviceName = "git2go-" + projectId.substring(0, 8);

        try {
            logService.pushLog(deploymentId,
                "🔄 Rolling back to: " + previousTaskDefArn);

            ecsClient.updateService(
                UpdateServiceRequest.builder()
                    .cluster(ecsCluster)
                    .service(serviceName)
                    .taskDefinition(previousTaskDefArn)
                    .forceNewDeployment(true)
                    .build()
            );

            logService.pushLog(deploymentId,
                "✅ Rollback initiated successfully");

        } catch (Exception e) {
            logService.pushLog(deploymentId,
                "❌ Rollback failed: " + e.getMessage());
            log.error("Rollback failed for project {}", projectId, e);
        }
    }

    // Gets current task definition ARN of running service
    public String getCurrentTaskDefArn(String projectId) {
        String serviceName = "git2go-" + projectId.substring(0, 8);
        try {
            DescribeServicesResponse response = ecsClient.describeServices(
                DescribeServicesRequest.builder()
                    .cluster(ecsCluster)
                    .services(serviceName)
                    .build()
            );

            if (!response.services().isEmpty()) {
                return response.services().get(0)
                    .taskDefinition();
            }
        } catch (Exception e) {
            log.warn("Could not get current task def: {}", e.getMessage());
        }
        return null;
    }

    private boolean serviceExists(String serviceName) {
        try {
            DescribeServicesResponse response = ecsClient.describeServices(
                DescribeServicesRequest.builder()
                    .cluster(ecsCluster)
                    .services(serviceName)
                    .build()
            );
            return !response.services().isEmpty() &&
                !response.services().get(0).status().equals("INACTIVE");
        } catch (Exception e) {
            return false;
        }
    }

    private void createService(String serviceName,
                                 String taskDefArn,
                                 LogStreamingService logService,
                                 String deploymentId) {
        ecsClient.createService(
            CreateServiceRequest.builder()
                .cluster(ecsCluster)
                .serviceName(serviceName)
                .taskDefinition(taskDefArn)
                .desiredCount(1)
                .launchType(LaunchType.FARGATE)
                .networkConfiguration(
                    NetworkConfiguration.builder()
                        .awsvpcConfiguration(
                            AwsVpcConfiguration.builder()
                                .assignPublicIp(AssignPublicIp.ENABLED)
                                .build()
                        )
                        .build()
                )
                .build()
        );

        logService.pushLog(deploymentId, "✅ ECS service created");
    }
}