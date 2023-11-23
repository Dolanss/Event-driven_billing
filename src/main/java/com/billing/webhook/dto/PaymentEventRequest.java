package com.billing.webhook.dto;

import com.billing.webhook.domain.PaymentEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentEventRequest(

        @NotBlank(message = "event_id is required")
        String eventId,

        @NotBlank(message = "invoice_id is required")
        String invoiceId,

        @NotNull(message = "event_type is required")
        PaymentEventType eventType,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        String currency,

        @NotBlank(message = "customer_id is required")
        String customerId,

        String metadata
) {}
