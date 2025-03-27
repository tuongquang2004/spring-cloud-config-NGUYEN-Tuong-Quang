package org.springframework.cloud.config.monitor.strategy;

import org.springframework.cloud.config.monitor.PropertyPathNotification;
import org.springframework.http.HttpHeaders;

import java.util.Map;

public interface NotificationStrategy {

    boolean supports(HttpHeaders headers, Map<String, Object> body);

    PropertyPathNotification extract(HttpHeaders headers, Map<String, Object> body);

}
