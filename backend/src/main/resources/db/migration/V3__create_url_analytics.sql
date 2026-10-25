CREATE TABLE url_analytics (
id BIGSERIAL PRIMARY KEY,
short_code VARCHAR(10) NOT NULL REFERENCES url_mappings(short_code) ON DELETE CASCADE,
click_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
click_date DATE NOT NULL,
ip_address VARCHAR(45),
user_agent TEXT,
referrer TEXT,
country_code CHAR(2),
device_type VARCHAR(20),
browser VARCHAR(50),
os VARCHAR(50)
);
CREATE INDEX idx_analytics_short_code_ts ON url_analytics(short_code, click_timestamp);
CREATE INDEX idx_analytics_click_date ON url_analytics(click_date);
CREATE INDEX idx_analytics_country ON url_analytics(country_code);
