CREATE TABLE IF NOT EXISTS audit_logs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_external_id  VARCHAR(255) NOT NULL,
    previous_status      VARCHAR(20),
    new_status           VARCHAR(20)  NOT NULL,
    event_id             VARCHAR(255) NOT NULL,
    event_type           VARCHAR(20)  NOT NULL,
    payload              TEXT,
    occurred_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_audit_new_status
        CHECK (new_status IN ('PENDING','PAID','FAILED','REFUNDED','CHARGEBACK')),
    CONSTRAINT chk_audit_event_type
        CHECK (event_type IN ('PAID','FAILED','REFUNDED','CHARGEBACK'))
);

CREATE INDEX idx_audit_logs_invoice     ON audit_logs (invoice_external_id);
CREATE INDEX idx_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_logs_event_id    ON audit_logs (event_id);
