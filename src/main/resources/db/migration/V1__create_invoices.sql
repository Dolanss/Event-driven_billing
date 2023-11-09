CREATE TABLE IF NOT EXISTS invoices (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id VARCHAR(255) NOT NULL UNIQUE,
    customer_id VARCHAR(255) NOT NULL,
    amount      NUMERIC(19, 2) NOT NULL,
    currency    CHAR(3)        NOT NULL,
    status      VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT chk_invoice_status
        CHECK (status IN ('PENDING','PAID','FAILED','REFUNDED','CHARGEBACK')),
    CONSTRAINT chk_invoice_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_invoices_customer ON invoices (customer_id);
CREATE INDEX idx_invoices_status   ON invoices (status);
