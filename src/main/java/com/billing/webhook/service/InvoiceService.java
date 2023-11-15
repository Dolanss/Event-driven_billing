package com.billing.webhook.service;

import com.billing.webhook.domain.Invoice;
import com.billing.webhook.domain.InvoiceStatus;
import com.billing.webhook.domain.PaymentEventType;
import com.billing.webhook.exception.InvalidStateTransitionException;
import com.billing.webhook.exception.InvoiceNotFoundException;
import com.billing.webhook.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final AuditService auditService;

    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice applyEvent(String externalId,
                              String customerId,
                              BigDecimal amount,
                              String currency,
                              PaymentEventType eventType,
                              String eventId,
                              String payload) {
        Invoice invoice = invoiceRepository.findByExternalId(externalId)
                .orElseGet(() -> createInvoice(externalId, customerId, amount, currency));

        InvoiceStatus targetStatus = resolveTargetStatus(eventType);
        if (!invoice.canTransitionTo(targetStatus)) {
            throw new InvalidStateTransitionException(externalId, invoice.getStatus(), targetStatus);
        }

        InvoiceStatus previous = invoice.getStatus();
        invoice.setStatus(targetStatus);
        invoiceRepository.save(invoice);

        auditService.record(externalId, previous, targetStatus, eventId, eventType, payload);
        log.info("Invoice {} transitioned {} -> {}", externalId, previous, targetStatus);
        return invoice;
    }

    private Invoice createInvoice(String externalId, String customerId, BigDecimal amount, String currency) {
        var invoice = Invoice.builder()
                .externalId(externalId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .status(InvoiceStatus.PENDING)
                .build();
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public Invoice findByExternalId(String externalId) {
        return invoiceRepository.findByExternalId(externalId)
                .orElseThrow(() -> new InvoiceNotFoundException(externalId));
    }

    private InvoiceStatus resolveTargetStatus(PaymentEventType type) {
        return switch (type) {
            case PAID -> InvoiceStatus.PAID;
            case FAILED -> InvoiceStatus.FAILED;
            case REFUNDED -> InvoiceStatus.REFUNDED;
            case CHARGEBACK -> InvoiceStatus.CHARGEBACK;
        };
    }
}
