package com.ds.ratelimiter.backend;

import com.ds.ratelimiter.model.ApiRequest;

/**
 * Fake microservice for payment-related requests.
 */
public class PaymentService implements BackendService {
    @Override
    public boolean canHandle(String path) {
        return path.startsWith("/payments");
    }

    @Override
    public String handle(ApiRequest request) {
        return "PaymentService handled " + request.getMethod() + " " + request.getPath();
    }
}
