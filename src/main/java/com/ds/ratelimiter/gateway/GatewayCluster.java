package com.ds.ratelimiter.gateway;

import com.ds.ratelimiter.backend.*;
import com.ds.ratelimiter.limiter.*;
import com.ds.ratelimiter.model.ApiRequest;
import com.ds.ratelimiter.model.ApiResponse;
import java.util.ArrayList;
import java.util.List;

public class GatewayCluster {
    private final RateLimitStore sharedStore;
    private final RateLimitConfig rateLimitConfig;
    private final List<ApiGatewayNode> nodes;
    private int roundRobinIndex = 0;

    public GatewayCluster(boolean useTb, int tbCap, double tbRefill,
		boolean useFw, long fwLen, int fwLim,
		boolean useSw, long swLen, int swLim,
		boolean useLs, double paymentsMinPercentage, int serverMaxRatePerMs) {
        this.sharedStore = new InMemoryDistributedRateLimitStore();
        this.rateLimitConfig = new RateLimitConfig(useTb, tbCap, tbRefill, useFw, fwLen, fwLim, useSw, swLen, swLim, useLs, paymentsMinPercentage, serverMaxRatePerMs);
        this.nodes = new ArrayList<>();

        List<BackendService> services = List.of(new UserService(), new OrderService(), new PaymentService());
        nodes.add(new ApiGatewayNode("Gateway-1", sharedStore, rateLimitConfig, services));
        nodes.add(new ApiGatewayNode("Gateway-2", sharedStore, rateLimitConfig, services));
        nodes.add(new ApiGatewayNode("Gateway-3", sharedStore, rateLimitConfig, services));
    }

    public synchronized ApiResponse sendRoundRobin(ApiRequest request) {
        ApiGatewayNode node = nodes.get(roundRobinIndex);
        roundRobinIndex = (roundRobinIndex + 1) % nodes.size();
        return node.handle(request);
    }

    public ApiResponse sendToNode(ApiRequest request, int nodeIndex) {
        return nodes.get(nodeIndex).handle(request);
    }

    public int getRemainingTokens(String clientId, String path) {
        return sharedStore.getRemainingTokens(clientId + "|" + path, rateLimitConfig);
    }

	public String getRemainingTokensDetailed(String clientId, String path) {
        return sharedStore.getRemainingTokensDetailed(clientId + "|" + path, rateLimitConfig);
    }

    public void resetLimiter() {
        sharedStore.reset();
    }
}