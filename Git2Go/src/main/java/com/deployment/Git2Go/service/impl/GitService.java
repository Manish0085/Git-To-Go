package com.deployment.Git2Go.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class GitService {

    // Clones repo to a temp directory and returns the path
    public Path cloneRepo(String repoUrl, String branch,
                          String deploymentId) throws Exception {

        Path workDir = Files.createTempDirectory("git2go-" + deploymentId);

        log.info("Cloning {} branch={} to {}",
            repoUrl, branch, workDir);

        Git.cloneRepository()
            .setURI(repoUrl)
            .setDirectory(workDir.toFile())
            .setBranch(branch)
            .setDepth(1)        // shallow clone — faster
            .call();

        log.info("Clone complete: {}", workDir);
        return workDir;
    }

    // Deletes the temp directory after build
    public void cleanup(Path workDir) {
        try {
            deleteDirectory(workDir.toFile());
            log.info("Cleaned up workspace: {}", workDir);
        } catch (Exception e) {
            log.warn("Could not clean up {}: {}", workDir, e.getMessage());
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}