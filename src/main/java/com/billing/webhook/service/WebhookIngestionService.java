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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookIngestionService {

    private final WebhookEventRepository eventRepository;
    private final EventQueue eventQueue;
    private final ObjectMapper objectMapper;

    @Transactional
    public WebhookResponse ingest(PaymentEventRequest request) {
        if (eventRepository.existsByEventId(request.eventId())) {
            log.warn("Duplicate event received: {}", request.eventId());
            throw new DuplicateEventException(request.eventId());
        }

        String rawPayload = serialize(request);

        WebhookEvent event = WebhookEvent.builder()
                .eventId(request.eventId())
                .invoiceExternalId(request.invoiceId())
                .eventType(request.eventType())
                .status(WebhookEventStatus.RECEIVED)
                .rawPayload(rawPayload)
                .retryCount(0)
                .build();
        eventRepository.save(event);

        eventQueue.publish(new WebhookEventMessage(request, rawPayload));
        log.info("Event {} accepted and queued", request.eventId());

        return WebhookResponse.accepted(request.eventId());
    }

    private String serialize(PaymentEventRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            return request.toString();
        }
    }
}
