package com.git2go.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * User ko dikhane ke liye — ye info GitHub webhook settings me daalega
 */
@Getter
@AllArgsConstructor
@Builder
public class WebhookConfigResponse {
    private boolean autoDeployEnabled;
    private String webhookUrl;      // GitHub me ye URL daalega
    private String webhookSecret;   // GitHub me ye secret daalega
}
