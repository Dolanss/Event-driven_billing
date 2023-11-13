package com.billing.webhook.queue;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory BlockingQueue simulating a RabbitMQ exchange.
 * Replace with RabbitTemplate.convertAndSend() for production.
 */
@Component
public class EventQueue {

    private final BlockingQueue<WebhookEventMessage> queue = new LinkedBlockingQueue<>(1000);

    public EventQueue(MeterRegistry meterRegistry) {
        meterRegistry.gauge("webhook.queue.size", Tags.empty(), queue, BlockingQueue::size);
    }

    public void publish(WebhookEventMessage message) {
        if (!queue.offer(message)) {
            throw new IllegalStateException("Event queue is full — backpressure triggered");
        }
    }

    public BlockingQueue<WebhookEventMessage> getQueue() {
        return queue;
    }
}
