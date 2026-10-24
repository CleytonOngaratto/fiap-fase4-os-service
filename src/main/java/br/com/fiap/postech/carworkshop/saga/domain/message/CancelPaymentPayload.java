package br.com.fiap.postech.carworkshop.saga.domain.message;

public record CancelPaymentPayload(String budgetId, String reason) implements SagaPayload {
}
