package com.billing.webhook.repository;

import com.billing.webhook.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByInvoiceExternalIdOrderByOccurredAtAsc(String invoiceExternalId);
}
