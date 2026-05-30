package com.billing.webhook.service;

import com.billing.webhook.domain.WebhookEvent;
import com.billing.webhook.domain.WebhookEventStatus;
import com.billing.webhook.dto.PaymentEventRequest;
import com.billing.webhook.dto.WebhookResponse;
import com.billing.webhook.exception.DuplicateEventException;
import com.billing.webhook.queue.EventQueue;
import com.billing.webhook.queue.WebhookEventMessage;
import com.billing.webhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookIngestionService {

    private final WebhookEventRepository eventRepository;
    private final EventQueue eventQueue;
    private final ObjectMapper objectMapper;

    @Transactional
    public WebhookResponse ingest(PaymentEventRequest request) {
        String rawPayload = serialize(request);

        WebhookEvent event = WebhookEvent.builder()
                .eventId(request.eventId())
                .invoiceExternalId(request.invoiceId())
                .eventType(request.eventType())
                .status(WebhookEventStatus.RECEIVED)
                .rawPayload(rawPayload)
                .retryCount(0)
                .build();

        try {
            eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate event received: {}", request.eventId());
            throw new DuplicateEventException(request.eventId());
        }

        publishAfterCommit(new WebhookEventMessage(request, rawPayload));
        log.info("Event {} accepted and scheduled for processing", request.eventId());

        return WebhookResponse.accepted(request.eventId());
    }

    private void publishAfterCommit(WebhookEventMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventQueue.publish(message);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    eventQueue.publish(message);
                } catch (RuntimeException ex) {
                    log.error("Event {} was persisted but could not be queued: {}",
                            message.request().eventId(), ex.getMessage());
                }
            }
        });
    }

    private String serialize(PaymentEventRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            return request.toString();
        }
    }
}
