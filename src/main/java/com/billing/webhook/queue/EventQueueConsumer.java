package com.billing.webhook.queue;

import com.billing.webhook.service.WebhookProcessingService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Dedicated consumer thread that drains the in-memory queue,
 * mimicking a RabbitMQ consumer with a single-thread virtual executor.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventQueueConsumer {

    private final EventQueue eventQueue;
    private final WebhookProcessingService processingService;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running = true;

    @PostConstruct
    void start() {
        executor.submit(this::consume);
        log.info("EventQueueConsumer started");
    }

    private void consume() {
        while (running) {
            try {
                WebhookEventMessage message = eventQueue.getQueue().poll(500, TimeUnit.MILLISECONDS);
                if (message != null) {
                    executor.submit(() -> processingService.process(message));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("EventQueueConsumer stopped");
    }

    @PreDestroy
    void stop() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
