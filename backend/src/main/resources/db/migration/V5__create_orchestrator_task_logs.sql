CREATE TABLE orchestrator_task_logs (
id BIGSERIAL PRIMARY KEY,
run_id UUID NOT NULL REFERENCES orchestrator_runs(id) ON DELETE CASCADE,
task_id VARCHAR(50) NOT NULL,
task_name VARCHAR(200) NOT NULL,
stage VARCHAR(30) NOT NULL,
status VARCHAR(30) NOT NULL,
attempt_number INT NOT NULL DEFAULT 1,
started_at TIMESTAMPTZ,
completed_at TIMESTAMPTZ,
latency_ms BIGINT,
result_payload JSONB,
error_detail TEXT,
policy_checks JSONB DEFAULT '[]'
);
CREATE INDEX idx_task_logs_run_id ON orchestrator_task_logs(run_id);
CREATE INDEX idx_task_logs_task_id ON orchestrator_task_logs(run_id, task_id);
CREATE INDEX idx_task_logs_status ON orchestrator_task_logs(status);
