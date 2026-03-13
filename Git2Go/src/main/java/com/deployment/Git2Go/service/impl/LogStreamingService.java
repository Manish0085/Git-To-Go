package com.deployment.Git2Go.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class LogStreamingService {

    private final RedisTemplate<String, String> redisTemplate;

    // In-memory map of active SSE connections
    private final Map<String, SseEmitter> emitters =
        new ConcurrentHashMap<>();

    public LogStreamingService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Called by dashboard to subscribe to live logs
    public SseEmitter subscribe(String deploymentId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> emitters.remove(deploymentId));
        emitter.onTimeout(()    -> emitters.remove(deploymentId));
        emitter.onError(e       -> emitters.remove(deploymentId));

        emitters.put(deploymentId, emitter);
        log.info("SSE subscriber added for deployment: {}", deploymentId);

        return emitter;
    }

    // Called by build/deploy services to push a log line
    public void pushLog(String deploymentId, String message) {
        String logLine = message;

        // Persist to Redis (so logs survive page refresh)
        String redisKey = "logs:" + deploymentId;
        redisTemplate.opsForList().rightPush(redisKey, logLine);
        redisTemplate.expire(redisKey,
            java.time.Duration.ofHours(24));

        // Push to live SSE if client is connected
        SseEmitter emitter = emitters.get(deploymentId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .data(logLine));
            } catch (IOException e) {
                emitters.remove(deploymentId);
                log.warn("SSE emitter removed for {}", deploymentId);
            }
        }
    }

    // Get all stored logs for a deployment (for page refresh)
    public java.util.List<String> getLogs(String deploymentId) {
        String redisKey = "logs:" + deploymentId;
        java.util.List<String> logs =
            redisTemplate.opsForList().range(redisKey, 0, -1);
        return logs != null ? logs : java.util.List.of();
    }

    // Called when deployment finishes — closes SSE connection
    public void complete(String deploymentId) {
        SseEmitter emitter = emitters.remove(deploymentId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .name("done")
                    .data("Deployment finished"));
                emitter.complete();
            } catch (IOException e) {
                log.warn("Could not complete emitter for {}", deploymentId);
            }
        }
    }
}