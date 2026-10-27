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

`TaskGraph` is the explicit dependency graph. Each `TaskNode` contains identity, SDLC stage, dependencies, an executor returning structured artifacts, declarative entry/exit evidence gates, bounded retry policy, an optional fallback, and approval/policy/rollback controls.

The engine ticks every 500 ms and submits every dependency-ready node to a bounded worker pool. Independent nodes therefore run concurrently. A per-run/task submission key prevents duplicate dispatch, including throughout retry delays.

```text
PENDING -> entry gate/policies -> AWAITING_APPROVAL -> APPROVED
   |                                      |               |
   `--------------------------------------'               v
                                                   IN_PROGRESS
                                                    /       \
                                      COMPLETED/FALLBACK    retry
                                                           /   \
                                                   fallback   FAILED
                                                               |
                                             reverse-topological rollback
                                                       + safe stop
```

Approval gates are checked **before** executing a high-impact task. Decisions are durable in `approval_requests`; only an ADMIN can decide them. Rejection or ten-minute expiry fails the node, records the reason, compensates completed nodes with rollback handlers, skips remaining pending work, and terminates the run.

Policy guardrails run before task execution. Security, compliance, and change-control policies can block work. Entry gates validate required upstream evidence and exit gates validate executor artifacts. Dependencies—not global stage barriers—provide synchronization, so independent paths can cross stage boundaries and execute concurrently without weakening their local gates.

## Dynamic re-planning

An ambiguous graph carries a `dynamicExpansionPending` flag so it cannot finish after its sole clarification node. A scheduled planner observes the structured clarification artifact and injects four nodes. Injected nodes use normal dependencies, evidence gates, retries, and telemetry; the flag clears only after successful expansion.

For changed upstream output, an administrator pauses the run and submits a reason plus revised artifacts. The engine refuses to re-plan across in-flight work, increments the context version, calculates the transitive descendant set, resets only affected work, supersedes pending or granted approvals whose evidence is stale, checkpoints the graph, and appends a `PLAN_REVISED` audit event. Reviewers then explicitly resume the run.

## Brownfield reasoning evidence

The brownfield requirements agent performs a bounded, read-only scan of the configured repository root. It inventories concrete API, service, persistence, security, frontend, build, and regression-test files; builds a symbol-reference index; extracts controller annotation and schema evidence; and emits a structured impact report plus complete Markdown. The next design task has an entry gate requiring that evidence. In packaged environments without source files, the analyzer explicitly reports `RUNTIME_CLASSPATH` mode and produces a reduced component inventory rather than pretending a source scan occurred.

The design and documentation nodes emit full design and OpenAPI content in their artifacts. They no longer use a filename as a substitute for an engineering deliverable. Execution remains read-only: the scenario proposes changes and evidence but does not silently mutate the repository.

## Failure and recovery behavior

- Task actions have a five-minute default deadline, are interrupt-cancelled on timeout, and retry attempts are bounded (three by default) with exponential backoff.
- Exhaustion invokes a bounded fallback when configured; otherwise it triggers deterministic reverse-topological compensation on completed nodes with handlers.
- ADMIN operators can pause/resume or request safe-stop. Safe-stop marks unstarted/awaiting nodes `SKIPPED`, supersedes their approvals, lets in-flight work reach a boundary, compensates eligible work, and finalizes `CANCELLED`.
- Every failed retry attempt and terminal result is stored in PostgreSQL, not only the final attempt.
- Worker and analytics executors are independently bounded, so analytics bursts cannot starve orchestration. Rejected orchestration submissions remain pending for a later scheduler tick.
- `SseEmitter` polling jobs cancel on completion, timeout, disconnect, or controller shutdown.
- The serialized DAG and metrics are checkpointed at control and task-transition boundaries, and the terminal snapshot supports historical visualization after a run leaves memory.

Executable adapters remain process-local, so active execution is not resumed after restart. Startup reconciliation now fails any orphaned `RUNNING`, `PAUSED`, or `SAFE_STOPPING` run closed, timestamps it, and appends `RUN_RECOVERY_SAFE_STOPPED` evidence. Production-grade resumption additionally requires versioned executable specifications, leases/idempotency keys, and reconciliation of orphaned `IN_PROGRESS` work.

## Observability and lineage

The run ID correlates API state, approval records, task logs, SSE events, and reliability metrics. Context includes both compatibility keys and namespaced `task.<id>.artifacts` values plus a dependency/attempt lineage map. The context version changes on graph expansion or governed revision.

`orchestration_audit_events` is an append-only lifecycle journal containing actor, event type, context version, correlation/causation IDs, payload, and a per-run SHA-256 hash chain. Payloads are recursively canonicalized before hashing and again during verification so PostgreSQL JSONB formatting cannot change the digest. A transaction-scoped PostgreSQL advisory lock serializes writers for each run across JVMs and prevents a forked chain head. The API exposes the journal and its integrity result to authorized reviewers. Task logs retain every attempt, stage, timing, artifacts, error, and policy-check JSON.

Reliability telemetry exposes success, retry and rollback rates, fallback and re-plan counts, elapsed time, MTTR, p50/p95 task latency, and approval counts. Snapshots are persisted throughout the run and at finalization.

Ordinary users can list and inspect only runs they initiated; administrators can inspect all runs to perform approval oversight. Approval records store the deciding administrator when a decision is submitted.

Application logs supplement the hash-chained database journal. Production should additionally restrict UPDATE/DELETE on the audit table, export events to immutable retention, and add distributed OpenTelemetry traces and sensitive-field redaction.

## Security decisions

- Public registration always assigns `USER`; administration requires an out-of-band elevation path.
- BCrypt protects passwords; Spring Security remains stateless; CORS is constrained to local UI origins by default.
- URL ownership is checked server-side rather than trusted from client data.
- Abuse-sensitive mutation endpoints use atomic Redis fixed-window limits; limiter failure is observable and fails open so Redis cannot take down authentication or redirects.
- Analytics IP addresses are stored as keyed HMAC pseudonyms, and user-agent/referrer fields are bounded before crossing the asynchronous boundary.
- Request DTOs constrain URL, alias, tag, pagination, date-range, approval, and injected-task inputs; the database independently enforces routeable aliases.
- Proxy-derived IPs are accepted only from a private/loopback/link-local immediate peer and are syntactically validated.
- Secrets in Compose/application configuration are demo defaults and must not be used for deployment.
- Admin authorization applies to approvals, injection, pause/resume, safe-stop, and re-planning. Every high-impact decision requires a reason and is audit attributed.

## Scaling path

The stateless URL API can scale horizontally behind an ingress with shared Redis/PostgreSQL. Eight-character Base62 codes provide roughly 218 trillion values, and collisions have a hard retry bound. Database connection pools, servlet concurrency, cache TTLs, executor sizes, queues, retention, and active-run capacity are externally configurable.

The orchestrator cannot safely scale horizontally in its current form. A production evolution would use a durable queue, node leases, idempotency keys, versioned graph/context storage, an outbox for events, and a single-writer or optimistic-concurrency protocol per run. High-volume analytics likewise needs a durable stream and aggregate tables rather than one database row per click.
