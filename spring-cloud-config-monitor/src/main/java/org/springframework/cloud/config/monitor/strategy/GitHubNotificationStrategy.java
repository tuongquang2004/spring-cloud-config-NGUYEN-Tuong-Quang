package org.springframework.cloud.config.monitor.strategy;

import org.springframework.cloud.config.monitor.PropertyPathNotification;
import org.springframework.http.HttpHeaders;

import java.util.Collections;
import java.util.Map;

public class GitHubNotificationStrategy implements NotificationStrategy {

    @Override
    public boolean supports(HttpHeaders headers, Map<String, Object> body) {
        return headers.containsKey("X-GitHub-Event");
    }

    @Override
    public PropertyPathNotification extract(HttpHeaders headers, Map<String, Object> body) {
        Object path = body.get("path");
        return new PropertyPathNotification(String.valueOf(path));

    }
}
