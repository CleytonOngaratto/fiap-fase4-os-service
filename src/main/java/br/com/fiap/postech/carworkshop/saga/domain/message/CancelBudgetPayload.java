package br.com.fiap.postech.carworkshop.saga.domain.message;

public record CancelBudgetPayload(String budgetId, String reason) implements SagaPayload {
}
