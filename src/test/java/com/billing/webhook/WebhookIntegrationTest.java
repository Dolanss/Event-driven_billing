package com.billing.webhook;

import com.billing.webhook.domain.InvoiceStatus;
import com.billing.webhook.domain.PaymentEventType;
import com.billing.webhook.domain.WebhookEventStatus;
import com.billing.webhook.dto.PaymentEventRequest;
import com.billing.webhook.repository.AuditLogRepository;
import com.billing.webhook.repository.InvoiceRepository;
import com.billing.webhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class WebhookIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired WebhookEventRepository webhookEventRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        webhookEventRepository.deleteAll();
        invoiceRepository.deleteAll();
    }

    @Test
    void postPaymentEvent_shouldAcceptAndProcessAsync() throws Exception {
        var request = buildRequest(UUID.randomUUID().toString(), "INV-001", PaymentEventType.PAID);

        mockMvc.perform(post("/webhooks/payment")
                        .with(httpBasic("stripe-provider", "super-secret-key"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.eventId").value(request.eventId()));

        // Wait for async processing
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var invoice = invoiceRepository.findByExternalId("INV-001");
            assertThat(invoice).isPresent();
            assertThat(invoice.get().getStatus()).isEqualTo(InvoiceStatus.PAID);
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var event = webhookEventRepository.findByEventId(request.eventId());
            assertThat(event).isPresent();
            assertThat(event.get().getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        });

        var auditLogs = auditLogRepository.findByInvoiceExternalIdOrderByOccurredAtAsc("INV-001");
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getNewStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void postPaymentEvent_shouldRejectDuplicates() throws Exception {
        String eventId = UUID.randomUUID().toString();
        var request = buildRequest(eventId, "INV-002", PaymentEventType.PAID);
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/webhooks/payment")
                        .with(httpBasic("stripe-provider", "super-secret-key"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        // Wait for persistence
        await().atMost(3, TimeUnit.SECONDS)
                .until(() -> webhookEventRepository.existsByEventId(eventId));

        mockMvc.perform(post("/webhooks/payment")
                        .with(httpBasic("stripe-provider", "super-secret-key"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        assertThat(webhookEventRepository.findAll()).hasSize(1);
    }

    @Test
    void postPaymentEvent_withoutAuth_shouldReturn401() throws Exception {
        var request = buildRequest(UUID.randomUUID().toString(), "INV-003", PaymentEventType.PAID);

        mockMvc.perform(post("/webhooks/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invoiceStatusMachine_shouldTransitionCorrectly() throws Exception {
        String invoiceId = "INV-004";

        // PENDING -> PAID
        sendEvent(UUID.randomUUID().toString(), invoiceId, PaymentEventType.PAID);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(invoiceRepository.findByExternalId(invoiceId).get().getStatus())
                        .isEqualTo(InvoiceStatus.PAID));

        // PAID -> REFUNDED
        sendEvent(UUID.randomUUID().toString(), invoiceId, PaymentEventType.REFUNDED);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(invoiceRepository.findByExternalId(invoiceId).get().getStatus())
                        .isEqualTo(InvoiceStatus.REFUNDED));

        var logs = auditLogRepository.findByInvoiceExternalIdOrderByOccurredAtAsc(invoiceId);
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getPreviousStatus()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(logs.get(0).getNewStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(logs.get(1).getPreviousStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(logs.get(1).getNewStatus()).isEqualTo(InvoiceStatus.REFUNDED);
    }

    @Test
    void auditLog_endpoint_shouldReturnHistory() throws Exception {
        String invoiceId = "INV-005";
        sendEvent(UUID.randomUUID().toString(), invoiceId, PaymentEventType.PAID);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(auditLogRepository.findByInvoiceExternalIdOrderByOccurredAtAsc(invoiceId)).isNotEmpty());

        mockMvc.perform(get("/webhooks/payment/{id}/audit", invoiceId)
                        .with(httpBasic("stripe-provider", "super-secret-key")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceExternalId").value(invoiceId))
                .andExpect(jsonPath("$[0].newStatus").value("PAID"));
    }

    @Test
    void validation_shouldRejectMissingFields() throws Exception {
        mockMvc.perform(post("/webhooks/payment")
                        .with(httpBasic("stripe-provider", "super-secret-key"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void actuatorHealth_shouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private PaymentEventRequest buildRequest(String eventId, String invoiceId, PaymentEventType type) {
        return new PaymentEventRequest(
                eventId,
                invoiceId,
                type,
                new BigDecimal("149.99"),
                "BRL",
                "customer-123",
                null
        );
    }

    private void sendEvent(String eventId, String invoiceId, PaymentEventType type) throws Exception {
        var request = buildRequest(eventId, invoiceId, type);
        mockMvc.perform(post("/webhooks/payment")
                        .with(httpBasic("stripe-provider", "super-secret-key"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }
}
