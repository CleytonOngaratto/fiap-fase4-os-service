package br.com.fiap.postech.carworkshop.saga.domain.entity;

public enum CompensationStep {
    REFUND_PAYMENT(true),
    CANCEL_PAYMENT(true),
    RESTORE_PARTS(false),
    RELEASE_PARTS(false),
    CANCEL_BUDGET(true),
    CANCEL_DIAGNOSIS(true);

    private final boolean awaitsParticipant;

    CompensationStep(boolean awaitsParticipant) {
        this.awaitsParticipant = awaitsParticipant;
    }

    public boolean awaitsParticipant() {
        return awaitsParticipant;
    }
}
