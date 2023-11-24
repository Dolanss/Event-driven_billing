package com.billing.webhook.exception;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(String externalId) {
        super("Invoice not found: " + externalId);
    }
}
