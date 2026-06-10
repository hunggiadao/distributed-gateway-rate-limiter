# Distributed Rate Limiter and API Gateway Demo

This is a Java Maven project that demonstrates the core idea of a **Distributed Rate Limiter** implemented in multiple distributed **API Gateways**. The demo uses a simple **Swing GUI** so you can visually test request routing, rate limiting, and backend service selection.

## 1. Project Goal

The goal is to show how multiple API Gateway nodes can share the same rate limit state. This prevents a client from bypassing the limit by sending requests through different gateway nodes.

In this demo:

```text
┌───────────────────────────────────────────────────────────┐
│                     ClientDemoFrame                       │
│ - Configures Path, Method, and Target Client ID           │
│ - Generates ApiRequest (UUID, ClientID, Path, Method)     │
└─────────────────────────────┬─────────────────────────────┘
                              │
                              ▼
            ╭──────────────────────────────────╮
            │ [TCP Packet: Raw ApiRequest Data]│
            ╰──────────────────────────────────╯
                              │
                              ▼
┌───────────────────────────────────────────────────────────┐
│              GatewayCluster (Load Balancer)               │
│ - Reconstructs ApiRequest from raw TCP stream             │
│ - Routes traffic via Round-Robin or Direct Node Match     │
└─────────────────────────────┬─────────────────────────────┘
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│ GatewayNode1 │      │ GatewayNode2 │      │ GatewayNode3 │
│ - Get Config │      │ - Get Config │      │ - Get Config │
│ - Call Redis │      │ - Call Redis │      │ - Call Redis │
└───────┬──────┘      └───────┬──────┘      └───────┬──────┘
        └─────────────────────┼─────────────────────┘
                              │
                              ▼
            ╭──────────────────────────────────╮
            │ [Query: consume(key, config)]    │
            ╰──────────────────────────────────╯
                              │
                              ▼
┌───────────────────────────────────────────────────────────┐
│     InMemoryDistributedRateLimitStore (Redis Service)     │
│                                                           │
│ 1. Preemptive Load Shedding (Drop server over-capacity)   │
│ 2. Evaluate User Algos (Fixed, Sliding, Token Bucket)     │
│ 3. Commit State (Deduct tokens universally if allowed)    │
│ 4. Return RateLimitDecision (Allowed or Rejected)         │
└─────────────────────────────┬─────────────────────────────┘
                              │
                              ▼
                  [ Is Request Allowed? ]
                 /                       \
             [NO]                         [YES]
              │                             │
              ▼                             ▼
┌───────────────────────────┐ ┌─────────────────────────────┐
│     Return Status 429     │ │    Backend Path Routing     │
│    (Too Many Requests)    │ │                             │
└─────────────┬─────────────┘ │ - /users    ──> UserService │
              │               │ - /orders   ──> OrderService│
              │               │ - /payments ──> PaymentSvc  │
              │               │ - <unknown> ──> 404         │
              │               └─────────────┬───────────────┘
              │                             │
              │                             ▼
              │               ┌─────────────────────────────┐
              │               │      Return Status 200      │
              │               │    (Handled By Backend)     │
              │               └─────────────┬───────────────┘
              └───────────────┬─────────────┘
                              │
                              ▼
            ╭──────────────────────────────────╮
            │ [TCP Packet: Raw ApiResponse]    │
            ╰──────────────────────────────────╯
                              │
                              ▼
┌───────────────────────────────────────────────────────────┐
│             GatewayCluster ──> ClientDemoFrame            │
│ - Flushes ApiResponse back via TCP stream                 │
│ - Client UI renders color-coded logs & updates token UI   │
└───────────────────────────────────────────────────────────┘
```

The project simulates three API Gateway nodes:

```text
Gateway-1
Gateway-2
Gateway-3
```

All three nodes share one rate limit store. In a real distributed system, this shared store could be Redis. In this project, it is simulated using an in-memory Java object.

## 2. Technologies Used

- Java 11+
- Maven
- Java Swing GUI
- No external framework is required

## 3. Main Concepts Demonstrated

### API Gateway

An API Gateway is the entry point between clients and backend services. In this project, the gateway:

1. Receives a request from the GUI.
2. Checks the rate limiter.
3. Routes allowed requests to the correct backend service.
4. Blocks excessive requests with status `429`.

### Distributed Rate Limiter

A distributed rate limiter controls how many requests a client can send across a whole system, not only to one server.

The important idea is that all gateway nodes share the same rate limit state. Therefore, a client cannot avoid the limit by changing from Gateway-1 to Gateway-2.

## 4. Project Structure

```text
distributed-rate-limiter-gateway/
├── pom.xml
├── README.md
└── src/main/java/com/ds/ratelimiter/
                         ├── Main.java
                         ├── backend/
                         │   ├── BackendService.java
                         │   ├── UserService.java
                         │   ├── OrderService.java
                         │   └── PaymentService.java
                         ├── gateway/
                         │   ├── ApiGatewayNode.java
                         │   └── GatewayCluster.java
                         ├── gui/
                         │   ├── GatewayDemoFrame.java
                         │   └── ClientDemoFrame.java
                         ├── limiter/
                         │   ├── RateLimitConfig.java
                         │   ├── RateLimitStore.java
                         │   ├── InMemoryDistributedRateLimitStore.java
                         │   └── RateLimitState.java
                         └── model/
                             ├── ApiRequest.java
                             ├── ApiResponse.java
                             └── RateLimitDecision.java
```

## 5. Important Classes

### `Main.java`

Starts GatewayDemo and ClientDemo GUIs.

### `ClientDemoFrame.java`

The main client-side GUI. It lets you:

- Choose a client ID.
- Choose an HTTP method.
- Choose an API path.
- Choose a gateway node or round-robin routing.
- Send one request.
- Send a burst of requests.
- View live request logs.
- View remaining tokens.

### `GatewayDemoFrame.java`

The main Cluster graphical interface. It lets you:

- Configure token bucket capacity, refill rate.
- Configure fixed window length, limit.
- Configure sliding window length, limit.
- Configure fleet load shedder settings.
- View live request logs.

### `GatewayCluster.java`

Simulates a load balancer. It contains:

- Three gateway nodes.
- One shared rate limit store.
- Backend services.
- Configs for server and clients (SLAs)

This is the class that demonstrates the distributed idea because all gateway nodes receive the same shared store.

### `ApiGatewayNode.java`

Represents one gateway node. Its workflow is:

```text
Receive request
→ Consult rate limiter from shared store
→ If blocked, return 429
→ If allowed, route to backend service
→ Return backend response
```

### `InMemoryDistributedRateLimitStore.java`

Simulates a shared distributed store like Redis.

The methods are synchronized to keep token updates safe when many requests are sent quickly.

### Backend Services

The project has three mockup backend services:

```text
/users     → UserService
/orders    → OrderService
/payments  → PaymentService
```

If the path is `/unknown`, the gateway returns `404` because no backend service matches it.

## 6. How to Run in NetBeans

1. Open NetBeans.
2. Choose **File → Open Project**.
3. Select the folder `distributed-rate-limiter-gateway`.
4. Wait for Maven to load the project.
5. Right-click the project.
6. Choose **Run**.

The Swing GUI should open.

## 7. How to Run Using Terminal

To rebuild the project, run:

```bash
mvn clean compile package
```

Then run the generated JAR with the Main class:

```bash
java -cp target/distributed-rate-limiter-gateway-1.0.0.jar "com.ds.ratelimiter.Main"
```

or

```bash
java -cp target/classes "com.ds.ratelimiter.Main"
```

## 8. How to Demo the Project

### Demo 1: Normal Request Routing

1. Keep the default client ID as `client-a`.
2. Select path `/users`.
3. Click **Send 1 Request**.
4. The log should show status `200` and `UserService handled GET /users`.

Then test:

```text
/orders    → OrderService
/payments  → PaymentService
/unknown   → 404 No backend service found
```

### Demo 2: Rate Limit Blocking

1. Keep bucket capacity as `5`.
2. Keep refill rate as `1.0`.
3. Set burst request count to `10`.
4. Click **Apply Config / Reset**.
5. Click **Send Burst**.

Expected result:

- The first requests return status `200`.
- After the tokens run out, later requests return status `429`.

This proves that the rate limiter blocks excessive traffic.

### Demo 3: Distributed Behavior Across Gateway Nodes

1. Set Gateway to **Round Robin**.
2. Set bucket capacity to `5`.
3. Set burst request count to `10`.
4. Click **Apply Config / Reset**.
5. Click **Send Burst**.

Expected result:

The requests will go through different gateways:

```text
Gateway-1
Gateway-2
Gateway-3
Gateway-1
Gateway-2
...
```

Even though requests use different gateway nodes, the rate limit is still shared. After the shared token bucket is empty, the system returns `429`.

This demonstrates the distributed rate limiter idea.

### Demo 4: Separate Clients Have Separate Limits

1. Use client ID `client-a`.
2. Send a burst until some requests are blocked.
3. Change client ID to `client-b`.
4. Click **Send 1 Request**.

Expected result:

`client-b` should still be allowed because it has a separate token bucket.

### Demo 5: Refill Behavior

1. Use client ID `client-a`.
2. Send a burst until blocked.
3. Wait a few seconds.
4. Click **Check Tokens** or send another request.

Expected result:

Tokens slowly return according to the refill rate.

## 9. Suggested Presentation Script

You can explain the project like this:

> This project demonstrates a distributed rate limiter with an API Gateway. The GUI acts as a client sending API requests. The gateway cluster has three gateway nodes to simulate multiple server instances. All gateway nodes share one rate limit store, which simulates a distributed store such as Redis. The rate limiter uses the token bucket algorithm. Each client has a limited number of tokens. Every request consumes one token, and tokens refill over time. When the bucket is empty, the gateway blocks the request with status 429. If the request is allowed, the gateway routes it to the correct backend service based on the path.

## 10. Limitations

This is an educational simulation, not a production system.

Current limitations:

- The shared store is in memory, not real Redis.
- The backend services are fake Java classes, not real HTTP services.
- There is no real network communication.
- There is no authentication or security layer.
- The GUI is designed for demonstration, not production monitoring.

## 11. Possible Future Improvements

Possible improvements include:

- Replace the in-memory store with Redis.
- Add real HTTP server endpoints.
- Add API keys or JWT authentication.
- Add per-route rate limit rules.
- Add metrics charts.
- Add persistent logs.
- Add unit tests.

## 12. Quick Summary

This project shows that an API Gateway can protect backend services by checking a shared distributed rate limiter before routing requests. The token bucket algorithm allows short bursts while still controlling the long-term request rate.
