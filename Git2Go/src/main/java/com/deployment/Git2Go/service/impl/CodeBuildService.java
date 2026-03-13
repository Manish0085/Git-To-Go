package com.deployment.Git2Go.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeBuildService {

    private final CodeBuildClient codeBuildClient;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String s3Bucket;

    @Value("${aws.codebuild.project-name}")
    private String codeBuildProject;

    @Value("${aws.ecr.registry-url}")
    private String ecrRegistryUrl;

    @Value("${aws.region}")
    private String awsRegion;

    // Zips the repo, uploads to S3, triggers CodeBuild
    // Returns the CodeBuild build ID
    public String startBuild(Path repoPath,
                              String deploymentId,
                              String imageTag,
                              LogStreamingService logService) throws Exception {

        logService.pushLog(deploymentId, "📦 Zipping source code...");

        // 1. Zip the repo
        String s3Key = "builds/" + deploymentId + "/source.zip";
        Path zipFile = zipDirectory(repoPath, deploymentId);

        logService.pushLog(deploymentId, "☁️ Uploading to S3...");

        // 2. Upload zip to S3
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(s3Bucket)
                .key(s3Key)
                .build(),
            RequestBody.fromFile(zipFile)
        );

        logService.pushLog(deploymentId,
            "🔨 Starting AWS CodeBuild...");

        // 3. Trigger CodeBuild
        String imageUri = ecrRegistryUrl + "/" +
            deploymentId.substring(0, 8) + ":" + imageTag;

        StartBuildResponse buildResponse = codeBuildClient.startBuild(
            StartBuildRequest.builder()
                .projectName(codeBuildProject)
                .sourceTypeOverride(SourceType.S3)
                .sourceLocationOverride(s3Bucket + "/" + s3Key)
                .environmentVariablesOverride(
                    EnvironmentVariable.builder()
                        .name("IMAGE_URI")
                        .value(imageUri)
                        .build(),
                    EnvironmentVariable.builder()
                        .name("AWS_DEFAULT_REGION")
                        .value(awsRegion)
                        .build(),
                    EnvironmentVariable.builder()
                        .name("ECR_REGISTRY")
                        .value(ecrRegistryUrl)
                        .build(),
                    EnvironmentVariable.builder()
                        .name("IMAGE_TAG")
                        .value(imageTag)
                        .build()
                )
                .build()
        );

        String buildId = buildResponse.build().id();
        log.info("CodeBuild started: {}", buildId);

        // Cleanup zip file
        Files.deleteIfExists(zipFile);

        return buildId;
    }

    // Polls CodeBuild until build completes or fails
    // Returns the ECR image URI on success
    public String waitForBuild(String buildId,
                                String deploymentId,
                                String imageTag,
                                LogStreamingService logService)
            throws Exception {

        String imageUri = ecrRegistryUrl + "/" +
            deploymentId.substring(0, 8) + ":" + imageTag;

        while (true) {
            BatchGetBuildsResponse response = codeBuildClient.batchGetBuilds(
                BatchGetBuildsRequest.builder()
                    .ids(buildId)
                    .build()
            );

            if (response.builds().isEmpty()) {
                throw new RuntimeException("Build not found: " + buildId);
            }

            Build build = response.builds().get(0);
            StatusType status = build.buildStatus();

            logService.pushLog(deploymentId,
                "⚙️ CodeBuild status: " + status);

            switch (status) {
                case SUCCEEDED -> {
                    logService.pushLog(deploymentId,
                        "✅ Build succeeded! Image: " + imageUri);
                    return imageUri;
                }
                case FAILED -> throw new RuntimeException(
                    "CodeBuild failed. Check AWS CodeBuild console.");
                case FAULT, STOPPED, TIMED_OUT -> throw new RuntimeException(
                    "CodeBuild ended with status: " + status);
                default -> {
                    // Still in progress — wait and poll again
                    Thread.sleep(10_000); // poll every 10 seconds
                }
            }
        }
    }

    // Zips a directory into a temp file
    private Path zipDirectory(Path sourceDir,
                               String deploymentId) throws IOException {
        Path zipFile = Files.createTempFile(
            "git2go-" + deploymentId, ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(
                new FileOutputStream(zipFile.toFile()))) {

            Files.walkFileTree(sourceDir,
                new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult visitFile(Path file,
                        BasicFileAttributes attrs) throws IOException {

                    String zipEntry = sourceDir
                        .relativize(file).toString();

                    // Skip .git folder
                    if (zipEntry.startsWith(".git")) {
                        return FileVisitResult.CONTINUE;
                    }

                    zos.putNextEntry(new ZipEntry(zipEntry));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return zipFile;
    }
}