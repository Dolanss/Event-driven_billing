package com.billing.webhook.controller;

import com.billing.webhook.domain.AuditLog;
import com.billing.webhook.dto.PaymentEventRequest;
import com.billing.webhook.dto.WebhookResponse;
import com.billing.webhook.service.AuditService;
import com.billing.webhook.service.WebhookIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookIngestionService ingestionService;
    private final AuditService auditService;

    @PostMapping("/payment")
    public ResponseEntity<WebhookResponse> receivePayment(
            @Valid @RequestBody PaymentEventRequest request) {
        log.info("Received payment event: type={} invoice={}", request.eventType(), request.invoiceId());
        WebhookResponse response = ingestionService.ingest(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/payment/{invoiceId}/audit")
    public ResponseEntity<List<AuditLog>> getAuditLog(@PathVariable String invoiceId) {
        return ResponseEntity.ok(auditService.getHistory(invoiceId));
    }
}
