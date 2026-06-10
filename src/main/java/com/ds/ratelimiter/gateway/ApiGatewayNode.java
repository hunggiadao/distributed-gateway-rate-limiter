package com.ds.ratelimiter.gateway;

import com.ds.ratelimiter.backend.BackendService;
import com.ds.ratelimiter.limiter.RateLimitConfig;
import com.ds.ratelimiter.limiter.RateLimitStore;
import com.ds.ratelimiter.model.ApiRequest;
import com.ds.ratelimiter.model.ApiResponse;
import com.ds.ratelimiter.model.RateLimitDecision;

import java.util.List;
import java.util.function.Function;

/**
 * Represents one API Gateway node.
 *
 * Real systems usually run multiple gateway instances for scalability and
 * availability. This demo creates three nodes: Gateway-1, Gateway-2,
 * and Gateway-3.
 *
 * Responsibilities of this class:
 * 1. Receive a request.
 * 2. Check the shared distributed rate limiter.
 * 3. If allowed, route the request to the correct backend service.
 * 4. If not allowed, return status 429.
 */
public class ApiGatewayNode {
    private final String name;
    private final RateLimitStore rateLimitStore; // 
    private final Function<String, RateLimitConfig> configProvider; // CHANGED
    private final List<BackendService> backendServices;

    public ApiGatewayNode(String name,
                          RateLimitStore rateLimitStore,
                          Function<String, RateLimitConfig> configProvider, // CHANGED
                          List<BackendService> backendServices) {
        this.name = name;
        this.rateLimitStore = rateLimitStore;
        this.configProvider = configProvider;
        this.backendServices = backendServices;
    }

    public String getName() {
        return name;
    }

    /**
     * Main gateway workflow.
     *
     * Notice the rate limiter is checked BEFORE the backend service is called.
     * This protects backend services from overload.
     */
    public ApiResponse handle(ApiRequest request) {
        // The client ID is used as the rate limit key.
        // Requests from the same client share one token bucket.
        String rateLimitKey = request.getClientId();
		// NEW: Look up the specific rules for THIS client before consuming tokens
        RateLimitConfig config = configProvider.apply(request.getClientId());
        RateLimitDecision decision = rateLimitStore.consume(rateLimitKey + "|" + request.getPath(), config);

        // 429 Too Many Requests: client has no tokens left.
        if (!decision.isAllowed()) {
            return new ApiResponse(
                    429,
                    decision.getReason() + " for client " + request.getClientId(),
                    name,
                    decision.getRemainingTokens()
            );
        }

        // Simple path-based routing. The first backend that can handle the path
        // receives the request.
        for (BackendService service : backendServices) {
            if (service.canHandle(request.getPath())) {
                String result = service.handle(request);
                return new ApiResponse(200, result, name, decision.getRemainingTokens());
            }
        }

        // Request was allowed by the limiter, but no backend path matched.
        return new ApiResponse(404, "No backend service found for " + request.getPath(), name, decision.getRemainingTokens());
    }
}
