package br.com.fiap.postech.carworkshop.saga.domain.message;

public sealed interface SagaPayload permits StartDiagnosisPayload, CancelDiagnosisPayload,
        CreateBudgetPayload, CancelBudgetPayload, RequestPaymentPayload, CancelPaymentPayload,
        RefundPaymentPayload, StartRepairPayload {
}
