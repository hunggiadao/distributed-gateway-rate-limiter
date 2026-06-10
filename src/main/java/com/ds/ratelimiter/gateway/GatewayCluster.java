package com.ds.ratelimiter.gateway;

import com.ds.ratelimiter.backend.*;
import com.ds.ratelimiter.limiter.*;
import com.ds.ratelimiter.model.ApiRequest;
import com.ds.ratelimiter.model.ApiResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The GatewayCluster class represents a cluster of API gateway nodes that manage
 * rate limiting for incoming API requests. It provides centralized configuration
 * management, dynamic client-specific rate limiting, and a round-robin request
 * distribution mechanism.
 */
public class GatewayCluster {
    private final RateLimitStore sharedStore; // shared Redis
    private final List<ApiGatewayNode> nodes; // list of gateways
    private int roundRobinIndex = 0;
	// NEW: Config Management
    private RateLimitConfig defaultConfig;
    private final Map<String, RateLimitConfig> clientConfigs = new ConcurrentHashMap<>();

    // CHANGED: Constructor now takes a single Config object
    public GatewayCluster(RateLimitConfig initialConfig) {
        this.sharedStore = new InMemoryDistributedRateLimitStore();
        this.defaultConfig = initialConfig;
        this.nodes = new ArrayList<>();

        List<BackendService> services = List.of(new UserService(), new OrderService(), new PaymentService());
        
        // Pass a reference to the getConfig method so nodes can look up rules
        nodes.add(new ApiGatewayNode("Gateway-1", sharedStore, this::getConfig, services));
        nodes.add(new ApiGatewayNode("Gateway-2", sharedStore, this::getConfig, services));
        nodes.add(new ApiGatewayNode("Gateway-3", sharedStore, this::getConfig, services));
    }

	// NEW & UPDATED: Get a config for a specific client (with universal fleet load shedder)
    public RateLimitConfig getConfig(String clientId) {
        RateLimitConfig clientConfig = clientConfigs.get(clientId);
        
        // Fall back to default if no client-specific configuration override exists
        if (clientConfig == null) {
            return defaultConfig;
        }
        
        // Dynamic Grafting: Combine client's token-based rules with the universal master load shedder rules
        return new RateLimitConfig(
                clientConfig.isTokenBucketEnabled(),
                clientConfig.getTbCapacity(),
                clientConfig.getTbRefillRate(),
                clientConfig.isFixedWindowEnabled(),
                clientConfig.getFwWindowLengthMs(),
                clientConfig.getFwLimit(),
                clientConfig.isSlidingWindowEnabled(),
                clientConfig.getSwWindowLengthMs(),
                clientConfig.getSwLimit(),
                
                // ALWAYS enforce the master server-side load shedder policy from defaultConfig
                defaultConfig.isLoadShedderEnabled(),
                defaultConfig.getPaymentsMinPercentage(),
                defaultConfig.getServerMaxRatePerMs()
        );
    }

    // NEW & UPDATED: Update config for a specific client or all clients globally
    public void updateConfig(String target, RateLimitConfig config) {
        if ("All Clients".equals(target)) {
            // Master configuration update (sets defaults and universal load shedder)
            this.defaultConfig = config;
        } else {
            // Save the client's custom token-based rules
            this.clientConfigs.put(target, config);
            
            // Universal Sync: Because the Fleet Load Shedder is a server-side global policy,
            // if it was altered while a specific client was selected, we push those load 
            // shedder values to defaultConfig so that ALL clients reflect the change immediately.
            this.defaultConfig = new RateLimitConfig(
                    defaultConfig.isTokenBucketEnabled(),
                    defaultConfig.getTbCapacity(),
                    defaultConfig.getTbRefillRate(),
                    defaultConfig.isFixedWindowEnabled(),
                    defaultConfig.getFwWindowLengthMs(),
                    defaultConfig.getFwLimit(),
                    defaultConfig.isSlidingWindowEnabled(),
                    defaultConfig.getSwWindowLengthMs(),
                    defaultConfig.getSwLimit(),
                    
                    // Harvest the updated load shedder settings from the UI configuration
                    config.isLoadShedderEnabled(),
                    config.getPaymentsMinPercentage(),
                    config.getServerMaxRatePerMs()
            );
        }
    }

	/**
     * Sends an API request to the next gateway node in a round-robin fashion.
     *
     * @param request The API request to be processed.
     * @return The response from the gateway node.
     */
    public synchronized ApiResponse sendRoundRobin(ApiRequest request) {
        ApiGatewayNode node = nodes.get(roundRobinIndex);
        roundRobinIndex = (roundRobinIndex + 1) % nodes.size();
        return node.handle(request);
    }

	/**
     * Sends an API request to a specific gateway node by index.
     *
     * @param request The API request to be processed.
     * @param nodeIndex The index of the target gateway node.
     * @return The response from the gateway node.
     */
    public ApiResponse sendToNode(ApiRequest request, int nodeIndex) {
        return nodes.get(nodeIndex).handle(request);
    }

    // CHANGED: Update these two methods to pass the correct client config to the DB
    public int getRemainingTokens(String clientId, String path) {
        return sharedStore.getRemainingTokens(clientId + "|" + path, getConfig(clientId));
    }

	/**
     * Retrieves detailed information about the remaining tokens for a specific client and API path.
     *
     * @param clientId The ID of the client.
     * @param path The API path.
     * @return Detailed information about the remaining tokens.
     */
	public String getRemainingTokensDetailed(String clientId, String path) {
        return sharedStore.getRemainingTokensDetailed(clientId + "|" + path, getConfig(clientId));
    }

	/**
     * Resets the rate limiter by clearing all stored tokens and configurations.
     */
    public void resetLimiter() {
        sharedStore.reset();
    }
}