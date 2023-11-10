package com.billing.webhook.repository;

import com.billing.webhook.domain.WebhookEvent;
import com.billing.webhook.domain.WebhookEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    boolean existsByEventId(String eventId);
    Optional<WebhookEvent> findByEventId(String eventId);
    List<WebhookEvent> findByStatusAndRetryCountLessThanAndNextRetryAtBefore(
            WebhookEventStatus status, int maxRetries, Instant now);
}
