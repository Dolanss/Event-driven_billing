package com.billing.webhook.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs",
        indexes = @Index(name = "idx_audit_invoice", columnList = "invoiceExternalId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String invoiceExternalId;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus newStatus;

    @Column(nullable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentEventType eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;

    @PrePersist
    void prePersist() {
        occurredAt = Instant.now();
    }
}
