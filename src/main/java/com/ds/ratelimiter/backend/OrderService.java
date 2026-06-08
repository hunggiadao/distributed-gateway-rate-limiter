package com.ds.ratelimiter.backend;

import com.ds.ratelimiter.model.ApiRequest;

/**
 * Fake microservice for order-related requests.
 */
public class OrderService implements BackendService {
    @Override
    public boolean canHandle(String path) {
        return path.startsWith("/orders");
    }

    @Override
    public String handle(ApiRequest request) {
        return "OrderService handled " + request.getMethod() + " " + request.getPath();
    }
}
