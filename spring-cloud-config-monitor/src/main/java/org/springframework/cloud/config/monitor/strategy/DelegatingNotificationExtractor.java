package org.springframework.cloud.config.monitor.strategy;

import org.springframework.cloud.config.monitor.PropertyPathNotification;
import org.springframework.cloud.config.monitor.PropertyPathNotificationExtractor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

public class DelegatingNotificationExtractor implements PropertyPathNotificationExtractor {

    private final List<NotificationStrategy> strategies;

    public DelegatingNotificationExtractor(List<NotificationStrategy> strategies) {
        this.strategies = strategies;
    }

    @Override
    public PropertyPathNotification extract(MultiValueMap<String, String> headers, Map<String, Object> request) {
        HttpHeaders httpHeaders = headers instanceof HttpHeaders ? (HttpHeaders) headers : new HttpHeaders(headers);

        return strategies.stream()
            .filter(s -> s.supports(httpHeaders, request))
            .findFirst()
            .map(s -> s.extract(httpHeaders, request))
            .orElse(null);
    }
}
