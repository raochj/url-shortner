# URL Shortener and Agentic SDLC Orchestrator

A runnable full-stack assessment prototype with two connected concerns:

- A Java 21/Spring Boot URL shortener with JWT authentication, ownership checks, PostgreSQL persistence, Redis caching and distributed throttling, privacy-preserving click analytics, and an Angular UI.
- A governed, stateful DAG executor that demonstrates requirements-to-release orchestration through greenfield, brownfield, and ambiguous scenarios.

The scenario tasks produce reviewable simulated engineering artifacts; they do not call an external LLM or modify this repository. That boundary keeps the demo deterministic while making autonomy, governance, retries, approvals, lineage, and metrics visible.

The brownfield impact-analysis task is not a placeholder: it performs a bounded read-only source scan when the configured codebase is available, indexes symbol references, API annotations and schema evidence, and emits both structured evidence and a complete Markdown impact report. Subsequent design and OpenAPI tasks emit their full artifact contents.

## Architecture at a glance

```text
Browser -> nginx/Angular -> Spring Boot API -> PostgreSQL
                         |                 -> Redis
                         `-> DAG orchestrator
                              |-- bounded worker pool
                              |-- policy guardrails
                              |-- PostgreSQL approvals and task logs
                              `-- SSE state/metric updates
```

The orchestrator stores run metadata, every task attempt, approval decisions, transition checkpoints, metrics, and a canonical hash-chained audit journal in PostgreSQL. PostgreSQL advisory locks serialize audit writers per run. The live executable graph is held in one backend process; a restart fails orphaned active runs closed with audit evidence instead of leaving them falsely active. Independent ready nodes run in parallel through a bounded `ThreadPoolTaskExecutor`; explicit dependencies and entry/exit evidence gates synchronize only the paths that require it.

## Technology

- Backend: Java 21, Spring Boot 3.5.16, Spring Security, JPA, Flyway, JJWT
- Data: PostgreSQL 16 and Redis 7
- Frontend: Angular 21, TypeScript 5.9, RxJS 7.8, SCSS
- Delivery: Maven, npm, Docker Compose, nginx
- Tests: JUnit 5, Mockito, MockMvc, Jasmine/Karma configuration, JaCoCo

## Run with Docker

Prerequisite: Docker with Compose v2.

```bash
docker compose up --build
```

Then open:

- UI: `http://localhost:4200`
- API and redirect service: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

Create a local environment file before the first start. The checked-in example intentionally contains no credentials:

```bash
cp .env.example .env
# Fill every value in .env with a unique, high-entropy secret.
```

Compose fails fast when a required secret is absent. For deployments, inject these values from the platform secret manager instead of an environment file. The Compose file binds PostgreSQL, Redis, and the backend to loopback; expose only the intended ingress in a deployment environment.

### Create users

Public registration always creates a `USER`; a client-supplied role is deliberately ignored to prevent privilege escalation.

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","email":"demo@example.com","password":"<your-password>"}'
```

Approval and manual-injection actions require `ADMIN`. For a local demonstration only, register `admin` and elevate it explicitly through the database:

```bash
docker compose exec postgres psql -U postgres -d urlshortener \
  -c "UPDATE users SET role='ADMIN' WHERE username='admin';"
```

Log in again after elevation.

## Local development

Export the four variables shown in `.env.example`, then start the data services:

```bash
docker compose up -d postgres redis
```

Run the backend with Java 21 and Maven:

```bash
cd backend
mvn spring-boot:run
```

Run the frontend with Node.js 20+:

```bash
cd frontend
npm ci
npm start
```

The Angular development proxy sends `/api` and short-link requests to port 8080.

## API surface

All protected requests use `Authorization: Bearer <token>`.

| Area | Endpoints |
|---|---|
| Authentication | `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/auth/me` |
| URLs | `POST/GET /api/v1/urls`, `GET/PUT/DELETE /api/v1/urls/{shortCode}` |
| Redirect | `GET /{shortCode}` (public) |
| Analytics | `GET /api/v1/analytics/{shortCode}/summary`, `GET /api/v1/analytics/{shortCode}/timeseries` |
| Orchestration | scenario catalogue, run start/list/state, DAG, metrics, logs, SSE events |
| Governance | approve/reject, inject, pause, resume, safe-stop, and governed re-plan endpoints (ADMIN) |
| Audit | `GET /api/v1/orchestration/runs/{runId}/audit-events` (owner or ADMIN) |

The approval request body is `{"approved":true|false,"comment":"..."}`; the same endpoint handles approve and reject decisions.

## Three assessment scenarios

- **Greenfield:** 12 tasks across requirements, design, implementation, testing, documentation, and release. Design and release require human approval; implementation branches execute in parallel.
- **Brownfield:** 9 tasks model a QR-code enhancement. Impact analysis feeds design, three implementation branches converge on testing, one implementation task fails once and succeeds on bounded retry, and release requires approval.
- **Ambiguous:** begins with only `CLARIFY-001`. Its structured result dynamically adds two parallel implementation tasks, a benchmark, and documentation while the run remains under the same scheduler and guardrails.

Every graph exposes explicit entry/exit evidence gates and a context version. Administrators can pause a live run, submit a reasoned revision to an upstream task, inspect the affected descendant set and revoked approvals, and resume only after review. Lifecycle decisions are written to a per-run SHA-256 hash-chained audit journal.

## Verification

```bash
cd backend
mvn verify

cd ../frontend
npm ci
npm run build -- --configuration production
npm audit
```

`mvn verify` runs the unit/web tests, JaCoCo reporting, PMD, and maximum-effort SpotBugs analysis. The frontend lockfile pins patched transitive versions for the Angular build toolchain, so the full `npm audit` (including development dependencies) is expected to report zero vulnerabilities.

Run these commands in the target environment before release. Docker availability, schema migration against a real PostgreSQL instance, load testing, and failover testing remain release gates rather than assumptions.
