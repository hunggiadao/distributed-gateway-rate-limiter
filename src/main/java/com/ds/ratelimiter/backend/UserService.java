package com.ds.ratelimiter.backend;

import com.ds.ratelimiter.model.ApiRequest;

/**
 * Fake microservice for user-related requests.
 */
public class UserService implements BackendService {
    @Override
    public boolean canHandle(String path) {
        // Any path beginning with /users is routed to this service.
        return path.startsWith("/users");
    }

    @Override
    public String handle(ApiRequest request) {
        return "UserService handled " + request.getMethod() + " " + request.getPath();
    }
}
