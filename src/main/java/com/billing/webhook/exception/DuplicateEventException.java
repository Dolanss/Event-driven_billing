package com.billing.webhook.exception;

public class DuplicateEventException extends RuntimeException {
    public DuplicateEventException(String eventId) {
        super("Duplicate event: " + eventId);
    }
}
