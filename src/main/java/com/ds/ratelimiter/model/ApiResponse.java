package com.ds.ratelimiter.model;

/**
 * Represents the result returned by the API Gateway.
 *
 * The demo uses HTTP-like status codes:
 * - 200 means the request was allowed and routed successfully.
 * - 404 means the gateway could not find a backend for the path.
 * - 429 means the distributed rate limiter rejected the request.
 */
public class ApiResponse {
    private final int statusCode;
    private final String message;
    private final String gatewayName;
    private final int remainingTokens;

    public ApiResponse(int statusCode, String message, String gatewayName, int remainingTokens) {
        this.statusCode = statusCode;
        this.message = message;
        this.gatewayName = gatewayName;
        this.remainingTokens = remainingTokens;
    }

	// getter methods
    public int getStatusCode() {return statusCode;}
    public String getMessage() {return message;}
    public String getGatewayName() {return gatewayName;}
    public int getRemainingTokens() {return remainingTokens;}
}
