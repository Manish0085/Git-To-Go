package com.git2go.platform.audit;

import com.git2go.platform.entity.AuditLog;
import com.git2go.platform.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Audit Aspect — @Auditable annotation wale methods ko intercept karta hai.
 *
 * Flow:
 * 1. Method call hone se PEHLE: user info, IP address capture
 * 2. Method EXECUTE hone do (ProceedingJoinPoint.proceed())
 * 3. Method ke BAAD: result (success/failure) ke saath audit log save
 *
 * @Around = before + after dono handle karta hai
 * @annotation(auditable) = sirf @Auditable wale methods pe chalega
 *
 * Interview: "AOP Aspect implement kiya hai with @Around advice. @Auditable annotated
 * methods ko proxy se intercept karta hai. SecurityContext se current user, RequestContext
 * se IP address, method parameters se resource info extract karta hai. Success pe
 * SUCCESS log, exception pe FAILURE log — dono cases me audit trail complete rehta hai.
 * Cross-cutting concern hai — existing code me ek line bhi change nahi."
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    /**
     * @Around — method ke before + after dono me execute hota hai.
     * ProceedingJoinPoint.proceed() actual method call karti hai.
     */
    @Around("@annotation(auditable)")
    public Object auditAction(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String action = auditable.action();
        String userEmail = getCurrentUserEmail();
        String ipAddress = getClientIpAddress();

        // Method parameters se resource info extract karo
        ResourceInfo resourceInfo = extractResourceInfo(joinPoint);

        try {
            // Actual method execute karo
            Object result = joinPoint.proceed();

            // SUCCESS — audit log save
            saveAuditLog(action, userEmail, resourceInfo, "SUCCESS", null, ipAddress);

            return result;

        } catch (Throwable ex) {
            // FAILURE — audit log save with error details
            saveAuditLog(action, userEmail, resourceInfo, "FAILURE", ex.getMessage(), ipAddress);

            // Exception re-throw — audit log silently save hoga, original error flow nahi tootega
            throw ex;
        }
    }

    // ==================== HELPERS ====================

    /**
     * SecurityContext se current authenticated user ka email nikalo
     */
    private String getCurrentUserEmail() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("Could not extract user email for audit: {}", e.getMessage());
        }
        return "anonymous";
    }

    /**
     * HTTP request se client IP address nikalo
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isEmpty()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not extract IP for audit: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Method parameters se resource info extract karo.
     *
     * Convention: method parameters me se:
     * - UUID type → resourceId
     * - String "userEmail" → skip (user info already captured)
     * - Request DTO → extract name if available
     */
    private ResourceInfo extractResourceInfo(ProceedingJoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        String resourceType = inferResourceType(methodName);
        String resourceId = null;
        String resourceName = null;

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                if (args[i] instanceof UUID uuid) {
                    resourceId = uuid.toString();
                }
                // Request DTOs se name extract
                if (args[i] != null) {
                    try {
                        var nameMethod = args[i].getClass().getMethod("getName");
                        Object name = nameMethod.invoke(args[i]);
                        if (name instanceof String s) {
                            resourceName = s;
                        }
                    } catch (NoSuchMethodException e) {
                        // DTO me getName nahi hai — koi baat nahi
                    } catch (Exception e) {
                        log.debug("Could not extract name from argument: {}", e.getMessage());
                    }
                }
            }
        }

        return new ResourceInfo(resourceType, resourceId, resourceName);
    }

    /**
     * Method name se resource type guess karo
     */
    private String inferResourceType(String methodName) {
        String lower = methodName.toLowerCase();
        if (lower.contains("project")) return "PROJECT";
        if (lower.contains("deploy")) return "DEPLOYMENT";
        if (lower.contains("webhook")) return "WEBHOOK";
        if (lower.contains("login") || lower.contains("signup") || lower.contains("logout")) return "AUTH";
        return "SYSTEM";
    }

    private void saveAuditLog(String action, String userEmail, ResourceInfo resource,
                               String result, String details, String ipAddress) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .userEmail(userEmail)
                    .resourceType(resource.type())
                    .resourceId(resource.id())
                    .resourceName(resource.name())
                    .result(result)
                    .details(details)
                    .ipAddress(ipAddress)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit: {} | {} | {} | {} | {}", action, userEmail, resource.type(), result, ipAddress);
        } catch (Exception e) {
            // Audit log save fail hone pe APPLICATION CRASH NAHI hona chahiye
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    private record ResourceInfo(String type, String id, String name) {}
}
