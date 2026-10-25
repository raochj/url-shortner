CREATE TABLE approval_requests (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
run_id UUID NOT NULL REFERENCES orchestrator_runs(id) ON DELETE CASCADE,
task_id VARCHAR(50) NOT NULL,
prompt TEXT NOT NULL,
status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
approver_id BIGINT REFERENCES users(id),
approver_comment TEXT,
created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
decided_at TIMESTAMPTZ,
expires_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '10 minutes')
);
CREATE INDEX idx_approvals_run_task ON approval_requests(run_id, task_id);
CREATE INDEX idx_approvals_status ON approval_requests(status);
