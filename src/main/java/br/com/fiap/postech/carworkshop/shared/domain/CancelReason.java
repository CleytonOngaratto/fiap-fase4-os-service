package br.com.fiap.postech.carworkshop.shared.domain;

public enum CancelReason {
    REJECTED_BY_CUSTOMER,
    APPROVAL_EXPIRED,
    OUT_OF_STOCK,
    PAYMENT_EXPIRED,
    PAYMENT_REQUEST_FAILED,
    REPAIR_FAILED,
    DIAGNOSIS_FAILED,
    BUDGET_CREATION_FAILED,
    CANCELED_BY_STAFF
}
