package com.billing.webhook.controller;

import com.billing.webhook.domain.Invoice;
import com.billing.webhook.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/{externalId}")
    public ResponseEntity<Invoice> getInvoice(@PathVariable String externalId) {
        return ResponseEntity.ok(invoiceService.findByExternalId(externalId));
    }
}
