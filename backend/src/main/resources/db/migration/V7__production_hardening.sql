ALTER TABLE url_mappings
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE orchestrator_runs
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN metrics_snapshot JSONB;

ALTER TABLE url_mappings
    ADD CONSTRAINT chk_custom_alias_routeable
    CHECK (custom_alias IS NULL OR custom_alias ~ '^[a-zA-Z0-9]{4,10}$');

CREATE INDEX idx_url_mappings_owner_active_created
    ON url_mappings(user_id, is_active, created_at DESC);

CREATE INDEX idx_url_analytics_code_date
    ON url_analytics(short_code, click_date);

CREATE INDEX idx_url_analytics_timestamp
    ON url_analytics(click_timestamp);

CREATE INDEX idx_task_logs_run_started
    ON orchestrator_task_logs(run_id, started_at);

CREATE INDEX idx_approvals_run_status
    ON approval_requests(run_id, status);

CREATE UNIQUE INDEX uq_approval_pending_task
    ON approval_requests(run_id, task_id)
    WHERE status = 'PENDING';
