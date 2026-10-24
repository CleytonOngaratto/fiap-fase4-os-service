package br.com.fiap.postech.carworkshop.saga.domain.message;

import java.math.BigDecimal;
import java.time.Instant;

public record RequestPaymentPayload(String budgetId, BigDecimal amount,
                                    Instant paymentDeadline) implements SagaPayload {
}
