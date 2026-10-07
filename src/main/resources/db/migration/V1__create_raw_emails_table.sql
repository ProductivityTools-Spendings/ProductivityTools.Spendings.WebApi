CREATE TABLE raw_emails (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(128) NOT NULL UNIQUE,
    thread_id VARCHAR(128) NOT NULL,
    source VARCHAR(50) NOT NULL,
    subject TEXT,
    attachment_name TEXT,
    email_date TIMESTAMPTZ,
    raw_html TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_raw_emails_status ON raw_emails(status);
