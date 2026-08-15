# API Gateway

The single entry point for every client-facing call in RingWatch — routes requests to the right
downstream service, validates JWTs before anything else runs, and rate-limits per caller with a
hand-rolled sliding-window limiter. See the root [README](../README.md#architecture) for how it
fits into the wider pipeline.

Spring Cloud Gateway (reactive/WebFlux), not Spring MVC — every filter here is non-blocking.

## Running it

```bash
mvn -pl common-lib install -DskipTests
mvn -pl api-gateway -am spring-boot:run
```

| Variable | Required | Default |
|---|---|---|
| `JWT_SECRET` | yes | — |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | no | `http://localhost:4318/v1/traces` |

Downstream service URIs and the rate-limit parameters below are hardcoded in
`src/main/resources/application.yml`, not environment-driven — this is a local-dev gateway with a
fixed topology, not a multi-environment deployment.

## Routes

| Route id | Path predicate | Downstream |
|---|---|---|
| `auth-service` | `/auth/**` | `http://localhost:8081` |
| `decision-engine-override` | `POST /transactions/*/override` | `http://localhost:8085` |
| `ingestion-service` | `/transactions/**` | `http://localhost:8082` |
| `audit-service` | `/audit/**` | `http://localhost:8087` |
| `fraud-ring-detection-service` | `/fraud-rings/**` | `http://localhost:8086` |
| `dashboard-gateway-service` | `/ws/alerts` | `ws://localhost:8091` |

The override route is matched *before* the general `/transactions/**` route so a `POST
/transactions/{id}/override` reaches decision-engine (which owns override state) instead of
ingestion-service (which only accepts new transactions).

CORS is configured for `http://localhost:5173` (the dashboard) via
`spring.cloud.gateway.globalcors`. That config only covers matched gateway routes — it does *not*
cover `/actuator/**`, which is reached directly and has its own separate
`management.endpoints.web.cors.*` allowance for the same origin.

## JWT validation (`JwtAuthenticationGlobalFilter`)

Runs first (`Ordered.HIGHEST_PRECEDENCE`) on every request:

1. Strips any client-supplied `X-User-Id`/`X-User-Role` headers unconditionally, on every path
   (including open ones), so a caller can never smuggle a spoofed identity downstream.
2. `/auth/login` and `/ws/alerts` are the only open paths. Everything else requires a valid
   `Authorization: Bearer <jwt>` header (via `common-lib`'s `JwtValidator`) or gets `401`.
3. On success, sets `X-User-Id` and `X-User-Role` from the validated token's claims — every
   downstream service trusts these headers as already-verified identity.
4. `/ws/alerts` is a special case: a browser `WebSocket` handshake can't set an `Authorization`
   header, so this filter never rejects that path. It opportunistically validates a `?token=`
   query param instead (if present) just to set `X-User-Id` for the rate limiter's benefit — the
   *authoritative* auth check for that route happens downstream, in dashboard-gateway-service's own
   handshake interceptor.

## Rate limiting (`RateLimitGlobalFilter` + `SlidingWindowRateLimiter`)

Runs second (`HIGHEST_PRECEDENCE + 1`), after JWT validation, so its per-caller key reflects
verified identity rather than a client-supplied header.

- **Algorithm**: sliding-window log — a per-key `ArrayDeque<Long>` of request timestamps
  (`ringwatch.gateway.ratelimit.SlidingWindowRateLimiter`). On each call, timestamps older than
  `now - window` are popped from the front; if what remains is already at `limit`, the request is
  rejected, otherwise the new timestamp is pushed and the request proceeds.
- **Key**: `user:<accountId>` when `X-User-Id` is set (authenticated request), else
  `ip:<remoteAddress>`.
- **Config** (`ringwatch.rate-limit.*`): `limit: 20`, `window: 10s`.
- **Rejection**: `429` with `{"message":"Rate limit exceeded"}`.

Every rejection is also recorded by `ThrottledKeyTracker`, which keeps the true cumulative
rejection count per key in a `HashMap` and maintains the current top-N (`top-throttled-size: 10`)
in a bounded min-heap (`ThrottleHeap`) — this is the service's DSA centerpiece (see root README).
That top-N view is exposed at `GET /actuator/throttledAccounts`, a custom Actuator endpoint
(`ThrottledAccountsEndpoint`) — unauthenticated, consistent with every other Actuator endpoint in
this system being operator-only/reachable-only-by-whoever-can-reach-the-gateway, not a gap unique
to this one.

## Testing

```bash
mvn -pl api-gateway -am test
```

Covers routing (`GatewayRoutingIntegrationTest`), rate-limit enforcement and post-window recovery
(`RateLimitGlobalFilterIntegrationTest`, `RateLimitRecoveryIntegrationTest`) against a live
`WebTestClient`, plus focused unit tests for the sliding-window limiter, the throttle heap, and the
key tracker.
