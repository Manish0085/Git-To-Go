package com.git2go.platform.util;

import com.git2go.platform.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Port Allocator — har container ko unique AND free host port assign karta hai.
 *
 * Interview: "AtomicInteger se thread-safe counter maintain karta hoon.
 * Har allocated port ko ServerSocket se bind karke verify karta hoon ki
 * actually free hai — sirf counter increment karna sufficient nahi hai
 * kyunki koi aur process us port pe already listen kar sakta hai."
 */
@Slf4j
@Component
public class PortAllocator {

    private static final int START_PORT = 10000;
    private static final int MAX_PORT = 65000;
    private static final int MAX_ATTEMPTS = 100; // kitni baar try karein free port dhundhne me

    private final AtomicInteger currentPort = new AtomicInteger(START_PORT);

    public int allocatePort() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int port = currentPort.getAndIncrement();

            // Wrap around
            if (port > MAX_PORT) {
                currentPort.set(START_PORT);
                port = START_PORT;
            }

            // Actually check ki port free hai
            if (isPortAvailable(port)) {
                log.debug("Allocated port: {}", port);
                return port;
            }

            log.debug("Port {} is in use, trying next...", port);
        }

        throw new ApiException("No free port available after " + MAX_ATTEMPTS + " attempts", HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Port free hai ya nahi — ServerSocket bind karke check karo.
     * Bind success = free, IOException = already in use
     */
    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
