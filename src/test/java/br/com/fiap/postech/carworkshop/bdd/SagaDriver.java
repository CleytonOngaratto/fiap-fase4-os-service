package br.com.fiap.postech.carworkshop.bdd;

import java.util.List;

public interface SagaDriver {

    void useOnlyServices();

    void usePartOutOfStock();

    void openWorkOrder(String branchId);

    void startDiagnosis();

    void completeDiagnosis();

    void completeDiagnosisWithUnknownItem();

    void failDiagnosis();

    void approveBudget();

    void rejectBudget();

    void pay();

    void startRepair();

    void completeRepair();

    void failRepair();

    void cancelWorkOrder();

    void deliverWorkOrder();

    void expireApprovalDeadline();

    void expirePaymentDeadline();

    void refuseRefunds();

    void takeExecutionDown();

    void bringExecutionBack();

    void assertWorkOrderStatus(String status);

    void assertWorkOrderCanceled(String status, String reason);

    void assertBudgetStatus(String status);

    void assertPaymentStatus(String status);

    void assertExecutionStatus(String status);

    void assertPartStock(int quantity, int reserved);

    void assertCancellationRefused(int httpStatus);

    void assertHistory(List<String> statuses);

    void assertRefundPending();

    void assertDiagnosisRequestWaiting();

    void assertUnknownItemNamed();
}
