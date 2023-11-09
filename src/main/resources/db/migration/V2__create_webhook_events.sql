CREATE TABLE IF NOT EXISTS webhook_events (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id              VARCHAR(255) NOT NULL UNIQUE,
    invoice_external_id   VARCHAR(255) NOT NULL,
    event_type            VARCHAR(20)  NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    raw_payload           TEXT,
    retry_count           INT          NOT NULL DEFAULT 0,
    next_retry_at         TIMESTAMPTZ,
    received_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at          TIMESTAMPTZ,
    last_error            TEXT,

    CONSTRAINT chk_event_type
        CHECK (event_type IN ('PAID','FAILED','REFUNDED','CHARGEBACK')),
    CONSTRAINT chk_event_status
        CHECK (status IN ('RECEIVED','PROCESSING','PROCESSED','FAILED','DEAD_LETTER')),
    CONSTRAINT chk_retry_count
        CHECK (retry_count >= 0)
);

CREATE INDEX idx_webhook_events_status_retry
    ON webhook_events (status, retry_count, next_retry_at)
    WHERE status = 'FAILED';

CREATE INDEX idx_webhook_events_invoice
    ON webhook_events (invoice_external_id);
