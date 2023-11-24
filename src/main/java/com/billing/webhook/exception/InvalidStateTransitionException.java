package com.billing.webhook.exception;

import com.billing.webhook.domain.InvoiceStatus;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(String invoiceId, InvoiceStatus from, InvoiceStatus to) {
        super("Cannot transition invoice %s from %s to %s".formatted(invoiceId, from, to));
    }
}
