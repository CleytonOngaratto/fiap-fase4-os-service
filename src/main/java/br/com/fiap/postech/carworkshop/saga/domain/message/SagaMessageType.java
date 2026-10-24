package br.com.fiap.postech.carworkshop.saga.domain.message;

import java.util.Map;

public final class SagaMessageType {

    public static final String START_DIAGNOSIS = "StartDiagnosis";
    public static final String CANCEL_DIAGNOSIS = "CancelDiagnosis";
    public static final String START_REPAIR = "StartRepair";
    public static final String CREATE_BUDGET = "CreateBudget";
    public static final String CANCEL_BUDGET = "CancelBudget";
    public static final String REQUEST_PAYMENT = "RequestPayment";
    public static final String CANCEL_PAYMENT = "CancelPayment";
    public static final String REFUND_PAYMENT = "RefundPayment";

    public static final String DIAGNOSIS_STARTED = "DiagnosisStarted";
    public static final String DIAGNOSIS_COMPLETED = "DiagnosisCompleted";
    public static final String DIAGNOSIS_FAILED = "DiagnosisFailed";
    public static final String DIAGNOSIS_CANCELED = "DiagnosisCanceled";
    public static final String REPAIR_STARTED = "RepairStarted";
    public static final String REPAIR_COMPLETED = "RepairCompleted";
    public static final String REPAIR_FAILED = "RepairFailed";
    public static final String BUDGET_CREATED = "BudgetCreated";
    public static final String BUDGET_CREATION_FAILED = "BudgetCreationFailed";
    public static final String BUDGET_APPROVED = "BudgetApproved";
    public static final String BUDGET_REJECTED = "BudgetRejected";
    public static final String BUDGET_CANCELED = "BudgetCanceled";
    public static final String PAYMENT_REQUESTED = "PaymentRequested";
    public static final String PAYMENT_REQUEST_FAILED = "PaymentRequestFailed";
    public static final String PAYMENT_ATTEMPT_REJECTED = "PaymentAttemptRejected";
    public static final String PAYMENT_CONFIRMED = "PaymentConfirmed";
    public static final String PAYMENT_CANCELED = "PaymentCanceled";
    public static final String PAYMENT_REFUNDED = "PaymentRefunded";

    private static final Map<String, SagaDestination> DESTINATIONS = Map.of(
            START_DIAGNOSIS, SagaDestination.EXECUTION,
            CANCEL_DIAGNOSIS, SagaDestination.EXECUTION,
            START_REPAIR, SagaDestination.EXECUTION,
            CREATE_BUDGET, SagaDestination.BILLING,
            CANCEL_BUDGET, SagaDestination.BILLING,
            REQUEST_PAYMENT, SagaDestination.BILLING,
            CANCEL_PAYMENT, SagaDestination.BILLING,
            REFUND_PAYMENT, SagaDestination.BILLING);

    private SagaMessageType() {
    }

    public static SagaDestination destinationOf(String type) {
        SagaDestination destination = DESTINATIONS.get(type);
        if (destination == null) {
            throw new IllegalArgumentException("No participant queue is defined for the command " + type + ".");
        }
        return destination;
    }
}
