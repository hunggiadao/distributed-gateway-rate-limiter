package com.ds.ratelimiter.backend;

import com.ds.ratelimiter.model.ApiRequest;

/**
 * Common interface for all fake backend services.
 *
 * The API Gateway does not need to know the internal logic of each service.
 * It only asks two questions:
 * 1. Can this service handle the path?
 * 2. If yes, what response should it return?
 */
public interface BackendService {
    boolean canHandle(String path);
    String handle(ApiRequest request);
}
