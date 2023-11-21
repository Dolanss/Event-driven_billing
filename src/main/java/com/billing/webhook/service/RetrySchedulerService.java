package com.billing.webhook.service;

import com.billing.webhook.domain.WebhookEvent;
import com.billing.webhook.domain.WebhookEventStatus;
import com.billing.webhook.dto.PaymentEventRequest;
import com.billing.webhook.queue.EventQueue;
import com.billing.webhook.queue.WebhookEventMessage;
import com.billing.webhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetrySchedulerService {

    private static final int MAX_RETRIES = 3;

    private final WebhookEventRepository eventRepository;
    private final EventQueue eventQueue;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void retryFailedEvents() {
        List<WebhookEvent> retryable = eventRepository
                .findByStatusAndRetryCountLessThanAndNextRetryAtBefore(
                        WebhookEventStatus.FAILED, MAX_RETRIES, Instant.now());

        if (retryable.isEmpty()) return;

        log.info("Retrying {} failed webhook event(s)", retryable.size());
        for (WebhookEvent event : retryable) {
            try {
                PaymentEventRequest req = objectMapper.readValue(
                        event.getRawPayload(), PaymentEventRequest.class);
                eventQueue.publish(new WebhookEventMessage(req, event.getRawPayload()));
                event.setStatus(WebhookEventStatus.RECEIVED);
                eventRepository.save(event);
            } catch (Exception ex) {
                log.error("Failed to re-queue event {}: {}", event.getEventId(), ex.getMessage());
            }
        }
    }
}
