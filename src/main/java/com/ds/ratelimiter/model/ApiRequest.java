package com.ds.ratelimiter.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents one incoming API request from a client.
 *
 * In a real API gateway, this information would come from an HTTP request:
 * - clientId could be an API key, user ID, IP address, or JWT subject.
 * - path could be /users, /orders, /payments, etc.
 * - method could be GET, POST, PUT, DELETE, etc.
 */
public class ApiRequest {
    private final String requestId; // automated
    private final String clientId;
    private final String path;
    private final String method;
    private final LocalDateTime createdAt; // automated

    public ApiRequest(String clientId, String path, String method) {
        // Short random ID makes each request easy to identify in the GUI log.
        this.requestId = UUID.randomUUID().toString().substring(0, 8);
        this.clientId = clientId;
        this.path = path;
        this.method = method;
        this.createdAt = LocalDateTime.now();
    }

	// getter methods
    public String getRequestId() { return requestId; }
    public String getClientId() { return clientId; }
    public String getPath() { return path; }
    public String getMethod() { return method; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
