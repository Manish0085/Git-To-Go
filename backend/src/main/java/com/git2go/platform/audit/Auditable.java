package com.git2go.platform.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom Annotation — kisi bhi method pe lagao, action automatically audit log me jaayega.
 *
 * Usage:
 *   @Auditable(action = "DEPLOY_PROJECT")
 *   public DeploymentResponse triggerDeployment(...) { ... }
 *
 * Interview: "@Auditable custom annotation hai — @Target(METHOD) se sirf methods pe
 * lag sakta hai. @Retention(RUNTIME) se runtime pe reflection se accessible hai —
 * AOP proxy isse detect karta hai. action field describe karta hai ki kya hua,
 * AOP aspect baaki context (user, result, timestamp) automatically capture karta hai."
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();  // e.g., "CREATE_PROJECT", "DEPLOY_PROJECT", "LOGIN"
}
