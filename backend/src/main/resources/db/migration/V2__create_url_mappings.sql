CREATE TABLE url_mappings (
id BIGSERIAL PRIMARY KEY,
short_code VARCHAR(10) NOT NULL UNIQUE,
long_url TEXT NOT NULL,
user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
custom_alias VARCHAR(50) UNIQUE,
is_active BOOLEAN NOT NULL DEFAULT TRUE,
expires_at TIMESTAMPTZ,
click_count BIGINT NOT NULL DEFAULT 0,
tags TEXT[] DEFAULT '{}',
created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
CONSTRAINT chk_short_code_alnum CHECK (short_code ~ '^[a-zA-Z0-9]{4,10}$'),
CONSTRAINT chk_url_format CHECK (long_url ~ '^https?://.+')
);
CREATE INDEX idx_url_mappings_user_id ON url_mappings(user_id);
CREATE INDEX idx_url_mappings_expires_at ON url_mappings(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_url_mappings_tags ON url_mappings USING GIN(tags);
