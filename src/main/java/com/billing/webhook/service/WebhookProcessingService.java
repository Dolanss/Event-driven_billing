package com.billing.webhook.service;

import com.billing.webhook.domain.WebhookEvent;
import com.billing.webhook.domain.WebhookEventStatus;
import com.billing.webhook.exception.InvalidStateTransitionException;
import com.billing.webhook.queue.WebhookEventMessage;
import com.billing.webhook.repository.WebhookEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookProcessingService {

    private static final int MAX_RETRIES = 3;

    private final WebhookEventRepository eventRepository;
    private final InvoiceService invoiceService;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void process(WebhookEventMessage message) {
        var req = message.request();
        var event = eventRepository.findByEventId(req.eventId())
                .orElseThrow(() -> new IllegalStateException("Event not found: " + req.eventId()));

        event.setStatus(WebhookEventStatus.PROCESSING);
        eventRepository.save(event);

        try {
            invoiceService.applyEvent(
                    req.invoiceId(),
                    req.customerId(),
                    req.amount(),
                    req.currency(),
                    req.eventType(),
                    req.eventId(),
                    message.rawPayload()
            );
            event.setStatus(WebhookEventStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            event.setLastError(null);
            meterRegistry.counter("webhook.processed",
                    Tags.of("type", req.eventType().name(), "result", "success")).increment();
            log.info("Event {} processed successfully", req.eventId());

        } catch (InvalidStateTransitionException ex) {
            // Invalid transition is a business rule violation — no retry
            log.warn("Event {} rejected: {}", req.eventId(), ex.getMessage());
            event.setStatus(WebhookEventStatus.DEAD_LETTER);
            event.setLastError(ex.getMessage());
            meterRegistry.counter("webhook.processed",
                    Tags.of("type", req.eventType().name(), "result", "invalid_transition")).increment();

        } catch (Exception ex) {
            handleFailure(event, ex, req.eventType().name());
        }

        eventRepository.save(event);
    }

    private void handleFailure(WebhookEvent event, Exception ex, String eventTypeName) {
        int attempts = event.getRetryCount() + 1;
        event.setRetryCount(attempts);
        event.setLastError(ex.getMessage());

        if (attempts >= MAX_RETRIES) {
            event.setStatus(WebhookEventStatus.DEAD_LETTER);
            log.error("Event {} exhausted retries, moved to dead-letter: {}", event.getEventId(), ex.getMessage());
            meterRegistry.counter("webhook.processed",
                    Tags.of("type", eventTypeName, "result", "dead_letter")).increment();
        } else {
            long backoffSeconds = (long) Math.pow(2, attempts) * 5; // 10s, 20s, 40s
            event.setStatus(WebhookEventStatus.FAILED);
            event.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
            log.warn("Event {} failed (attempt {}), retry in {}s: {}",
                    event.getEventId(), attempts, backoffSeconds, ex.getMessage());
            meterRegistry.counter("webhook.processed",
                    Tags.of("type", eventTypeName, "result", "failed_retryable")).increment();
        }
    }
}
