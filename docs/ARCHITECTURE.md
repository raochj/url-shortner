# Architecture and engineering decisions

## System boundaries

The Angular SPA calls a Spring Boot API through nginx. Spring Security authenticates protected `/api/v1/**` requests with a JWT and reloads the user from PostgreSQL, so current role and active status remain authoritative. Public routes are registration, login, health/info, Swagger/OpenAPI, and the constrained short-code redirect route.

PostgreSQL owns durable users, URL mappings, analytics events, orchestration runs, task logs, and approval requests. Redis is an optimization: redirects fall back to PostgreSQL on a cache miss or Redis outage, and click increments are buffered before periodic persistence.

## URL flow

1. A user submits an HTTP(S) target with an optional alias, title, tags, and expiry.
2. Validation rejects malformed/non-HTTP targets and addresses that resolve into local/private/link-local/multicast space.
3. The service checks alias uniqueness and stores an owner-scoped mapping.
4. A public redirect resolves Redis first and PostgreSQL second, captures immutable analytics fields, increments the buffered click count, and submits analytics to a dedicated bounded executor.
5. CRUD and analytics access enforce ownership in the service layer.

`CacheService` uses Redis `SCAN`, not blocking `KEYS`, and uses `GETDEL` when flushing counters. A failed database update restores the delta to Redis; a missing mapping discards an orphaned counter. Cache TTL never outlives URL expiry.

## Orchestration model

`TaskGraph` is the explicit dependency graph. Each `TaskNode` contains identity, SDLC stage, dependencies, an executor returning structured artifacts, bounded retry policy, optional approval/policy/rollback controls, and observable mutable state.

The engine ticks every 500 ms and submits every dependency-ready node to a bounded worker pool. Independent nodes therefore run concurrently. A per-run/task submission key prevents duplicate dispatch, including throughout retry delays.

```text
PENDING -> policy/stage checks -> AWAITING_APPROVAL -> APPROVED
   |                                      |               |
   `--------------------------------------'               v
                                                   IN_PROGRESS
                                                    /       \
                                             COMPLETED    retry
                                                           |
                                                        FAILED
                                                           |
                                             rollback + safe stop
```

Approval gates are checked **before** executing a high-impact task. Decisions are durable in `approval_requests`; only an ADMIN can decide them. Rejection or ten-minute expiry fails the node, records the reason, compensates completed nodes with rollback handlers, skips remaining pending work, and terminates the run.

Policy guardrails run before task execution. Blocking findings prevent execution; warnings remain observable and allow progression. Stage-entry checks add a second synchronization boundary in addition to explicit dependencies.

## Dynamic re-planning

An ambiguous graph carries a `dynamicExpansionPending` flag so it cannot finish after its sole clarification node. A scheduled planner observes the structured clarification artifact and injects four nodes. Injected nodes use normal dependencies, stage checks, retries, and telemetry; the flag clears only after successful expansion.

If upstream output changed in a production implementation, the planner would version the context, invalidate descendants, and require renewed approval for materially changed work. This prototype demonstrates initial late-bound expansion, not arbitrary plan revision after execution.

## Failure and recovery behavior

- Retry attempts are bounded (three by default) and use exponential backoff.
- Exhaustion triggers reverse-order best-effort compensation on completed nodes with handlers.
- Safe-stop marks unstarted/awaiting nodes `SKIPPED`; no new downstream work begins.
- The engine records per-attempt failure timestamps in memory and terminal task logs in PostgreSQL.
- Worker and analytics executors are independently bounded, so analytics bursts cannot starve orchestration. Rejected orchestration submissions remain pending for a later scheduler tick.
- `SseEmitter` polling jobs cancel on completion, timeout, disconnect, or controller shutdown.
- A final serialized DAG snapshot supports historical visualization after a run leaves memory.

Active-run restart recovery is deliberately not claimed: executable closures and complete cross-stage context are not checkpointed. Durable approval rows alone do not make execution restart-safe. Production would persist node specifications and context at every transition, use optimistic locking/leases, and reconcile orphaned `IN_PROGRESS` tasks on startup.

## Observability and lineage

The run ID correlates API state, approval records, task logs, SSE events, and reliability metrics. The API exposes total/completed/failed/rolled-back tasks, retry count, success and rollback rates, elapsed time, MTTR, p50/p95 task latency, and approval counts. Terminal metrics are persisted with the run. Task logs retain attempt, stage, timing, artifact payload, error, and policy-check JSON.

Ordinary users can list and inspect only runs they initiated; administrators can inspect all runs to perform approval oversight. Approval records store the deciding administrator when a decision is submitted.

Application logs supplement this audit record but are not themselves a tamper-evident audit store. Production controls should add trace IDs, OpenTelemetry, append-only retention, access monitoring, and sensitive-field redaction.

## Security decisions

- Public registration always assigns `USER`; administration requires an out-of-band elevation path.
- BCrypt protects passwords; Spring Security remains stateless; CORS is constrained to local UI origins by default.
- URL ownership is checked server-side rather than trusted from client data.
- Abuse-sensitive mutation endpoints use atomic Redis fixed-window limits; limiter failure is observable and fails open so Redis cannot take down authentication or redirects.
- Analytics IP addresses are stored as keyed HMAC pseudonyms, and user-agent/referrer fields are bounded before crossing the asynchronous boundary.
- Request DTOs constrain URL, alias, tag, pagination, date-range, approval, and injected-task inputs; the database independently enforces routeable aliases.
- Proxy-derived IPs are accepted only from a private/loopback/link-local immediate peer and are syntactically validated.
- Secrets in Compose/application configuration are demo defaults and must not be used for deployment.
- Admin authorization applies to approvals and manual task injection.

## Scaling path

The stateless URL API can scale horizontally behind an ingress with shared Redis/PostgreSQL. Eight-character Base62 codes provide roughly 218 trillion values, and collisions have a hard retry bound. Database connection pools, servlet concurrency, cache TTLs, executor sizes, queues, retention, and active-run capacity are externally configurable.

The orchestrator cannot safely scale horizontally in its current form. A production evolution would use a durable queue, node leases, idempotency keys, versioned graph/context storage, an outbox for events, and a single-writer or optimistic-concurrency protocol per run. High-volume analytics likewise needs a durable stream and aggregate tables rather than one database row per click.
