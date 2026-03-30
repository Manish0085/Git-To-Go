package com.git2go.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async Configuration — background tasks ke liye thread pool.
 *
 * Interview: "Custom thread pool configure kiya hai @Async tasks ke liye.
 * Core pool 5 threads, max 10, queue 25. Isse ek time pe max 10 builds
 * parallel chal sakti hain. Thread pool exhausted ho toh requests queue
 * me wait karengi — system overload nahi hoga."
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "buildExecutor")
    public Executor buildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("build-");
        executor.initialize();
        return executor;
    }
}
