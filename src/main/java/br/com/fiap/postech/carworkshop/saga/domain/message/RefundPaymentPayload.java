package br.com.fiap.postech.carworkshop.saga.domain.message;

public record RefundPaymentPayload(String paymentId, String reason) implements SagaPayload {
}
