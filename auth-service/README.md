# Auth Service

Analyst account management, login, and JWT issuance for RingWatch. Every other service trusts the
JWT this service signs — see the root [README](../README.md#architecture) for how identity flows
from here through the API Gateway to the rest of the pipeline.

REST-only, no Kafka involvement, backed by its own tables in the shared `ringwatch_auth` Postgres
database.

## Running it

```bash
mvn -pl common-lib install -DskipTests
mvn -pl auth-service -am spring-boot:run
```

| Variable | Required | Default |
|---|---|---|
| `JWT_SECRET` | yes | — |
| `RINGWATCH_ADMIN_PASSWORD` | yes | — |
| `RINGWATCH_ADMIN_USERNAME` | no | `admin` |

Listens on **:8081**. Reads/writes `ringwatch_auth` on the Postgres container brought up by the
root `docker compose up -d`.

## Endpoints

| Method | Path | Auth | Body | Notes |
|---|---|---|---|---|
| `POST` | `/auth/login` | none | `{ username, password }` | Returns `{ token, expiresInSeconds }` on success, 401 on bad credentials |
| `GET` | `/auth/me` | any valid JWT | — | Returns the calling account's own profile, resolved from the JWT subject (account ID) |
| `POST` | `/auth/accounts` | JWT with role `ADMIN` | `{ username, password (min 8 chars), role }` | Creates a new analyst/admin account; 403 for non-admin callers |

`/actuator/health` and `/actuator/prometheus` are open (no auth), matching every other service.

## JWT

Tokens are signed HS256 (`io.jsonwebtoken` / jjwt) using `JWT_SECRET` as the HMAC key — the same
secret every other RingWatch service is configured with, so any service can independently verify a
token without calling back into auth-service. Claims: `sub` (account UUID), `username`, `role`
(`ADMIN` or `ANALYST`), `iat`, `exp`. Expiry is a fixed 1 hour (`ringwatch.jwt.expiration-ms:
3600000`), not currently configurable via env var.

Passwords are hashed with `BCryptPasswordEncoder` (Spring Security default strength) — never stored
or logged in plaintext.

## Admin seeding

On startup, `AdminAccountSeeder` seeds one `ADMIN` account from `RINGWATCH_ADMIN_USERNAME` /
`RINGWATCH_ADMIN_PASSWORD` — but only if **no `ADMIN` account exists yet** in the database, not
based on whether that specific username is free. On a fresh database this seeds normally; on a
database that already has an admin (e.g. a reused Postgres volume from a prior run), it's a no-op
and `RINGWATCH_ADMIN_PASSWORD` is silently ignored — the existing account's password stands. If the
configured username is already taken by a *non-admin* account, seeding is skipped with a warning
log rather than failing startup.

## Testing

```bash
mvn -pl auth-service -am test
```

Unit tests cover the service/JWT layer; controller tests run against an in-memory H2 database
(`spring-boot-starter-test` + `spring-security-test` + `h2`), so the suite doesn't need Postgres.
