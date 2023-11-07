package com.billing.webhook.domain;

public enum PaymentEventType {
    PAID,
    FAILED,
    REFUNDED,
    CHARGEBACK
}
