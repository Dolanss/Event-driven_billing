package com.billing.webhook.dto;

public record WebhookResponse(
        String eventId,
        String status,
        String message
) {
    public static WebhookResponse accepted(String eventId) {
        return new WebhookResponse(eventId, "ACCEPTED", "Event queued for processing");
    }

    public static WebhookResponse duplicate(String eventId) {
        return new WebhookResponse(eventId, "DUPLICATE", "Event already received");
    }
}
