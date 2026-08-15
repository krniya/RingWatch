# Dashboard Gateway Service

Consumes `notifications.alerts` from Kafka and broadcasts every alert, live, to every currently
connected analyst browser over WebSocket — the one genuinely push-driven data source in the
RingWatch dashboard (everything else the frontend shows is polled). See the root
[README](../README.md#live-alerts) for how this fits into the wider pipeline.

## Running it standalone

```bash
mvn -pl dashboard-gateway-service -am spring-boot:run
```

| Variable | Required | Default |
|---|---|---|
| `JWT_SECRET` | yes | — |
| `DASHBOARD_ALLOWED_ORIGIN` | no | `http://localhost:5173` |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | no | `http://localhost:4318/v1/traces` |

Runs on **:8091**. In the full local stack the dashboard connects through the API Gateway, not
directly to this port — Spring Cloud Gateway proxies `ws://`/`wss://` routes the same way it
proxies HTTP, and the dashboard's `VITE_WS_BASE_URL` defaults to the gateway's origin (see
[dashboard-ui/README.md](../dashboard-ui/README.md)).

## Protocol

```
ws://localhost:8080/ws/alerts?token=<jwt>
```

A browser's `WebSocket` constructor has no API for setting an `Authorization` header, so the JWT
travels as a `?token=` query param instead and is validated at the handshake by
`JwtHandshakeInterceptor` — using the same `JwtValidator` (from `common-lib`) every other service
uses to validate the same header-based token elsewhere. A missing or invalid token gets `401` at
the handshake and the connection never opens. `WebSocketConfig` also restricts the handshake to
`DASHBOARD_ALLOWED_ORIGIN`.

Once connected, every session just receives broadcasts — there's no client→server message
protocol; a session is registered in `AlertSessionRegistry` on connect and unregistered on close.

### Message shape

Each broadcast is the JSON serialization of an `AlertEvent` (`common-lib`), sent unmodified from
what `notification-service` published to `notifications.alerts`:

```json
{
  "alertId": "…",
  "alertType": "…",
  "transactionId": "…",
  "ringId": "…",
  "message": "…",
  "createdAt": "2026-01-01T00:00:00Z"
}
```

No display "tone" (e.g. which color a toast should render) is computed server-side — that mapping
already lives client-side in the dashboard (`useOverrideDecision.js`'s outcome→tone map), and this
service deliberately doesn't duplicate it.

## Delivery guarantees

Broadcast is global and best-effort:

- **Every connected browser sees every alert** — there's no per-analyst targeting anywhere in
  RingWatch (even email alerts go to one shared recipient list), so this doesn't introduce
  per-user filtering either.
- **No missed-alert catch-up.** The Kafka consumer group ID is randomized per instance start
  (`dashboard-gateway-service-${random.uuid}` in `application.yml`, paired with
  `auto-offset-reset: latest`), so a restarted instance never replays alerts published while it was
  down — it only sees new ones from the moment it (re)connects. A disconnected browser similarly
  just misses whatever was broadcast while it was offline.
- Sending to each session happens on its own thread from a cached thread pool
  (`AlertSessionRegistry`), off the single Kafka listener thread — one slow or stalled browser
  can't delay delivery to everyone else or delay the consumer's offset commit. A send failure is
  logged and the session stays registered for the next broadcast; it's never treated as fatal
  (which would otherwise trigger Spring Kafka's default retry and re-broadcast the same alert to
  every already-delivered session).

## Testing

```bash
mvn -pl dashboard-gateway-service -am test
```

Covers the handshake interceptor (`JwtHandshakeInterceptorTest`), the session registry
(`AlertSessionRegistryTest`), and an end-to-end Kafka→broadcast path against `@EmbeddedKafka`
(`AlertBroadcastIntegrationTest`) — no Docker required.
