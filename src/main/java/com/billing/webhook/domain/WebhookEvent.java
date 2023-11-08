package com.billing.webhook.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events",
        indexes = @Index(name = "idx_event_id", columnList = "eventId", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String invoiceExternalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookEventStatus status;

    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    @Column(nullable = false)
    private int retryCount;

    private Instant nextRetryAt;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    private Instant processedAt;

    private String lastError;

    @PrePersist
    void prePersist() {
        receivedAt = Instant.now();
        if (status == null) {
            status = WebhookEventStatus.RECEIVED;
        }
    }
}
