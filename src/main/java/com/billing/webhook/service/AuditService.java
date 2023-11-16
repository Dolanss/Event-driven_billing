package com.billing.webhook.service;

import com.billing.webhook.domain.AuditLog;
import com.billing.webhook.domain.InvoiceStatus;
import com.billing.webhook.domain.PaymentEventType;
import com.billing.webhook.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String invoiceExternalId,
                       InvoiceStatus from,
                       InvoiceStatus to,
                       String eventId,
                       PaymentEventType eventType,
                       String payload) {
        var log = AuditLog.builder()
                .invoiceExternalId(invoiceExternalId)
                .previousStatus(from)
                .newStatus(to)
                .eventId(eventId)
                .eventType(eventType)
                .payload(payload)
                .build();
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getHistory(String invoiceExternalId) {
        return auditLogRepository.findByInvoiceExternalIdOrderByOccurredAtAsc(invoiceExternalId);
    }
}
