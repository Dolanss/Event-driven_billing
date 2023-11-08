package com.billing.webhook.domain;

public enum WebhookEventStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    FAILED,
    DEAD_LETTER
}
