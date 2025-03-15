package org.springframework.cloud.config.server.exception;

public class ConfigurationRetrievalException extends RuntimeException {

    public ConfigurationRetrievalException(String message) {
        super(message);
    }

    public ConfigurationRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}

