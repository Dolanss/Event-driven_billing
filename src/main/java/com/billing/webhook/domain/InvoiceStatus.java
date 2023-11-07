package com.billing.webhook.domain;

public enum InvoiceStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED,
    CHARGEBACK
}
