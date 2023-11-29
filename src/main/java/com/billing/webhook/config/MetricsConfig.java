package com.billing.webhook.config;

import com.billing.webhook.repository.WebhookEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class MetricsConfig {

    private final MeterRegistry meterRegistry;
    private final WebhookEventRepository eventRepository;

    public MetricsConfig(MeterRegistry meterRegistry, WebhookEventRepository eventRepository) {
        this.meterRegistry = meterRegistry;
        this.eventRepository = eventRepository;
    }

    @PostConstruct
    void registerGauges() {
        Gauge.builder("webhook.events.total", eventRepository, r -> r.count())
                .description("Total webhook events stored")
                .register(meterRegistry);
    }
}
