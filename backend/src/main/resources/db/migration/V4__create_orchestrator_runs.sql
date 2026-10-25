CREATE TABLE orchestrator_runs (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
scenario_type VARCHAR(20) NOT NULL,
status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
parameters JSONB DEFAULT '{}',
dag_snapshot JSONB,
started_at TIMESTAMPTZ,
completed_at TIMESTAMPTZ,
initiated_by BIGINT REFERENCES users(id),
created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_orch_runs_status ON orchestrator_runs(status);
CREATE INDEX idx_orch_runs_scenario ON orchestrator_runs(scenario_type);
