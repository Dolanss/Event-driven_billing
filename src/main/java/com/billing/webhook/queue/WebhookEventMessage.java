package com.billing.webhook.queue;

import com.billing.webhook.dto.PaymentEventRequest;

public record WebhookEventMessage(
        PaymentEventRequest request,
        String rawPayload
) {}
