package br.com.fiap.postech.carworkshop.saga.domain.entity;

import java.util.List;

public enum SagaState {
    AWAITING_DIAGNOSIS(List.of()),
    DIAGNOSING(List.of(CompensationStep.CANCEL_DIAGNOSIS)),
    AWAITING_BUDGET(List.of(CompensationStep.CANCEL_DIAGNOSIS)),
    AWAITING_APPROVAL(List.of(
            CompensationStep.CANCEL_BUDGET,
            CompensationStep.CANCEL_DIAGNOSIS)),
    AWAITING_PAYMENT_LINK(List.of(
            CompensationStep.RELEASE_PARTS,
            CompensationStep.CANCEL_BUDGET,
            CompensationStep.CANCEL_DIAGNOSIS)),
    AWAITING_PAYMENT(List.of(
            CompensationStep.CANCEL_PAYMENT,
            CompensationStep.RELEASE_PARTS,
            CompensationStep.CANCEL_BUDGET,
            CompensationStep.CANCEL_DIAGNOSIS)),
    AWAITING_REPAIR(List.of(
            CompensationStep.REFUND_PAYMENT,
            CompensationStep.RESTORE_PARTS,
            CompensationStep.CANCEL_BUDGET,
            CompensationStep.CANCEL_DIAGNOSIS)),
    REPAIRING(List.of(
            CompensationStep.REFUND_PAYMENT,
            CompensationStep.RESTORE_PARTS,
            CompensationStep.CANCEL_BUDGET,
            CompensationStep.CANCEL_DIAGNOSIS)),
    COMPLETED(List.of()),
    COMPENSATING(List.of()),
    COMPENSATED(List.of());

    private final List<CompensationStep> compensations;

    SagaState(List<CompensationStep> compensations) {
        this.compensations = compensations;
    }

    public List<CompensationStep> compensations() {
        return compensations;
    }

    public boolean isPastPivot() {
        return this == COMPLETED;
    }

    public boolean isCompensating() {
        return this == COMPENSATING || this == COMPENSATED;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPENSATED;
    }
}
