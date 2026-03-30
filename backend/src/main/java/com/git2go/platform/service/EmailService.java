package com.git2go.platform.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Email Service — HTML emails bhejta hai deployment events pe.
 *
 * @Async — emails background me jaate hain, API response block nahi hota.
 * Thymeleaf templates — professional HTML emails with dynamic content.
 * All sends wrapped in try-catch — SMTP fail pe app crash nahi hoga.
 *
 * Interview: "Async email service implement ki hai. Thymeleaf se HTML templates
 * render karta hoon with dynamic variables. SMTP failure gracefully handle hota hai —
 * email delivery failure deployment ko affect nahi karta. MimeMessage use kiya hai
 * HTML content + proper encoding ke liye."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.email.from:noreply@git2go.com}")
    private String fromEmail;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    /**
     * Email verification — signup ke baad, verify karo pehle
     */
    @Async("buildExecutor")
    public void sendVerificationEmail(String toEmail, String userName, String verificationUrl) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("verificationUrl", verificationUrl);

        sendHtmlEmail(toEmail, "Verify your email — Git2Go", "verify-email", context);
    }

    /**
     * Welcome email — verification ke baad
     */
    @Async("buildExecutor")
    public void sendWelcomeEmail(String toEmail, String userName) {
        Context context = new Context();
        context.setVariable("userName", userName);

        sendHtmlEmail(toEmail, "Welcome to Git2Go!", "welcome", context);
    }

    /**
     * Deployment success email
     */
    @Async("buildExecutor")
    public void sendDeploymentSuccessEmail(String toEmail, String projectName,
                                            String deployedUrl, int version) {
        Context context = new Context();
        context.setVariable("projectName", projectName);
        context.setVariable("deployedUrl", deployedUrl);
        context.setVariable("version", version);

        sendHtmlEmail(toEmail, "Deployment Successful: " + projectName, "deploy-success", context);
    }

    /**
     * Deployment failure email
     */
    @Async("buildExecutor")
    public void sendDeploymentFailedEmail(String toEmail, String projectName,
                                           String failureReason, int version) {
        Context context = new Context();
        context.setVariable("projectName", projectName);
        context.setVariable("failureReason", failureReason);
        context.setVariable("version", version);

        sendHtmlEmail(toEmail, "Deployment Failed: " + projectName, "deploy-failed", context);
    }

    // ==================== PRIVATE ====================

    private void sendHtmlEmail(String to, String subject, String templateName, Context context) {
        if (!emailEnabled) {
            log.debug("Email disabled. Skipping: {} to {}", subject, to);
            return;
        }

        try {
            // Thymeleaf template render → HTML string
            String htmlContent = templateEngine.process(templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML

            mailSender.send(message);
            log.info("Email sent: '{}' to {}", subject, to);

        } catch (Exception e) {
            // Email fail pe APPLICATION CRASH NAHI hona chahiye
            // Deployment successful hai — email sirf notification hai
            log.error("Failed to send email '{}' to {}: {}", subject, to, e.getMessage());
        }
    }
}
