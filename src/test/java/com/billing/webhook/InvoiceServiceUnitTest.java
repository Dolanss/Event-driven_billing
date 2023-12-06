package com.billing.webhook;

import com.billing.webhook.domain.Invoice;
import com.billing.webhook.domain.InvoiceStatus;
import com.billing.webhook.domain.PaymentEventType;
import com.billing.webhook.exception.InvalidStateTransitionException;
import com.billing.webhook.repository.InvoiceRepository;
import com.billing.webhook.service.AuditService;
import com.billing.webhook.service.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceUnitTest {

    @Mock InvoiceRepository invoiceRepository;
    @Mock AuditService auditService;

    InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, auditService);
    }

    @Test
    void applyEvent_PAID_createsInvoiceAndTransitions() {
        when(invoiceRepository.findByExternalId("INV-01")).thenReturn(Optional.empty());
        when(invoiceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        invoiceService.applyEvent("INV-01", "cust-1", BigDecimal.TEN, "BRL",
                PaymentEventType.PAID, "evt-1", "{}");

        verify(invoiceRepository, times(2)).save(any()); // create + update
        verify(auditService).record(eq("INV-01"), eq(InvoiceStatus.PENDING), eq(InvoiceStatus.PAID),
                eq("evt-1"), eq(PaymentEventType.PAID), eq("{}"));
    }

    @Test
    void applyEvent_REFUNDED_fromPAID_succeeds() {
        var invoice = paidInvoice();
        when(invoiceRepository.findByExternalId("INV-02")).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        invoiceService.applyEvent("INV-02", "cust-1", BigDecimal.TEN, "BRL",
                PaymentEventType.REFUNDED, "evt-2", "{}");

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.REFUNDED);
    }

    @Test
    void applyEvent_PAID_fromREFUNDED_throws() {
        var invoice = refundedInvoice();
        when(invoiceRepository.findByExternalId("INV-03")).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() ->
                invoiceService.applyEvent("INV-03", "cust-1", BigDecimal.TEN, "BRL",
                        PaymentEventType.PAID, "evt-3", "{}"))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("REFUNDED");

        verify(invoiceRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void invoiceStateMachine_terminalStatesAreImmutable() {
        assertThat(chargebackInvoice().canTransitionTo(InvoiceStatus.PAID)).isFalse();
        assertThat(chargebackInvoice().canTransitionTo(InvoiceStatus.REFUNDED)).isFalse();
        assertThat(refundedInvoice().canTransitionTo(InvoiceStatus.PAID)).isFalse();
    }

    private Invoice paidInvoice() {
        return Invoice.builder().externalId("INV-02").status(InvoiceStatus.PAID)
                .amount(BigDecimal.TEN).currency("BRL").customerId("c").build();
    }

    private Invoice refundedInvoice() {
        return Invoice.builder().externalId("INV-03").status(InvoiceStatus.REFUNDED)
                .amount(BigDecimal.TEN).currency("BRL").customerId("c").build();
    }

    private Invoice chargebackInvoice() {
        return Invoice.builder().externalId("INV-04").status(InvoiceStatus.CHARGEBACK)
                .amount(BigDecimal.TEN).currency("BRL").customerId("c").build();
    }
}
