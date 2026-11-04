package br.com.fiap.postech.carworkshop.bdd.steps;

import br.com.fiap.postech.carworkshop.bdd.SagaDriver;
import br.com.fiap.postech.carworkshop.bdd.SagaScenario;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;

import java.util.Arrays;
import java.util.List;

public class SagaSteps {

    private static final String BRANCH = "SP-01";

    private static final String AWAITING_DIAGNOSIS = "aguardando diagnóstico";
    private static final String DIAGNOSING = "diagnóstico em andamento";
    private static final String AWAITING_APPROVAL = "orçamento aguardando aprovação";
    private static final String AWAITING_PAYMENT = "pagamento pendente";
    private static final String REPAIRING = "reparo em andamento";

    private static SagaDriver driver() {
        return SagaScenario.driver();
    }

    @Dado("uma OS aberta na filial {string}")
    public void aWorkOrderOpenedAt(String branchId) {
        driver().openWorkOrder(branchId);
    }

    @Dado("uma OS na etapa {string}")
    public void aWorkOrderAt(String stage) {
        reach(stage);
    }

    @Dado("uma OS só com serviços na etapa {string}")
    public void aServicesOnlyWorkOrderAt(String stage) {
        driver().useOnlyServices();
        reach(stage);
    }

    @Dado("uma OS cujo orçamento inclui uma peça sem estoque")
    public void aWorkOrderWhoseBudgetHasAPartOutOfStock() {
        driver().usePartOutOfStock();
        reach(AWAITING_APPROVAL);
    }

    private static void reach(String stage) {
        if (!List.of(AWAITING_DIAGNOSIS, DIAGNOSING, AWAITING_APPROVAL, AWAITING_PAYMENT, REPAIRING).contains(stage)) {
            throw new IllegalArgumentException("Etapa desconhecida: " + stage);
        }
        driver().openWorkOrder(BRANCH);
        if (stage.equals(AWAITING_DIAGNOSIS)) {
            return;
        }
        driver().startDiagnosis();
        driver().assertWorkOrderStatus("UNDER_DIAGNOSIS");
        if (stage.equals(DIAGNOSING)) {
            return;
        }
        driver().completeDiagnosis();
        driver().assertWorkOrderStatus("PENDING_APPROVAL");
        if (stage.equals(AWAITING_APPROVAL)) {
            return;
        }
        driver().approveBudget();
        driver().assertWorkOrderStatus("AWAITING_PAYMENT");
        if (stage.equals(AWAITING_PAYMENT)) {
            return;
        }
        driver().pay();
        driver().assertWorkOrderStatus("IN_PROGRESS");
        driver().startRepair();
    }

    @Dado("o provedor recusa o estorno")
    public void theProviderRefusesRefunds() {
        driver().refuseRefunds();
    }

    @Dado("o Execution está fora do ar")
    public void theExecutionIsDown() {
        driver().takeExecutionDown();
    }

    @Quando("o Execution volta")
    public void theExecutionComesBack() {
        driver().bringExecutionBack();
    }

    @Quando("o mecânico inicia o diagnóstico")
    public void theMechanicStartsTheDiagnosis() {
        driver().startDiagnosis();
    }

    @Quando("o mecânico conclui o diagnóstico")
    public void theMechanicCompletesTheDiagnosis() {
        driver().completeDiagnosis();
    }

    @Quando("o mecânico conclui o diagnóstico com um item fora do catálogo")
    public void theMechanicCompletesTheDiagnosisWithAnUnknownItem() {
        driver().completeDiagnosisWithUnknownItem();
    }

    @Quando("o mecânico registra que o diagnóstico é inviável")
    public void theMechanicRecordsAFailedDiagnosis() {
        driver().failDiagnosis();
    }

    @Quando("o cliente aprova o orçamento")
    public void theCustomerApprovesTheBudget() {
        driver().approveBudget();
    }

    @Quando("o cliente rejeita o orçamento")
    public void theCustomerRejectsTheBudget() {
        driver().rejectBudget();
    }

    @Quando("o cliente paga")
    public void theCustomerPays() {
        driver().pay();
    }

    @Quando("o mecânico inicia o reparo")
    public void theMechanicStartsTheRepair() {
        driver().startRepair();
    }

    @Quando("o mecânico conclui o reparo")
    public void theMechanicCompletesTheRepair() {
        driver().completeRepair();
    }

    @Quando("o mecânico registra que o reparo falhou")
    public void theMechanicRecordsAFailedRepair() {
        driver().failRepair();
    }

    @Quando("o atendimento cancela a OS")
    public void theStaffCancelsTheWorkOrder() {
        driver().cancelWorkOrder();
    }

    @Quando("o atendimento registra a entrega")
    public void theStaffRecordsTheDelivery() {
        driver().deliverWorkOrder();
    }

    @Quando("o prazo de aprovação vence")
    public void theApprovalDeadlineExpires() {
        driver().expireApprovalDeadline();
    }

    @Quando("o prazo de pagamento vence")
    public void thePaymentDeadlineExpires() {
        driver().expirePaymentDeadline();
    }

    @Entao("a OS fica {string}")
    public void theWorkOrderIs(String status) {
        driver().assertWorkOrderStatus(status);
    }

    @Entao("a OS termina {string} com motivo {string}")
    public void theWorkOrderEnds(String status, String reason) {
        driver().assertWorkOrderCanceled(status, reason);
    }

    @Entao("o orçamento fica {string}")
    public void theBudgetIs(String status) {
        driver().assertBudgetStatus(status);
    }

    @Entao("o pagamento fica {string}")
    public void thePaymentIs(String status) {
        driver().assertPaymentStatus(status);
    }

    @Entao("o item de execução fica {string}")
    public void theExecutionItemIs(String status) {
        driver().assertExecutionStatus(status);
    }

    @Entao("o estoque da peça fica em {int} com {int} reservadas")
    public void thePartStockIs(int quantity, int reserved) {
        driver().assertPartStock(quantity, reserved);
    }

    @Entao("o cancelamento da OS é recusado com {int}")
    public void theCancellationIsRefused(int httpStatus) {
        driver().assertCancellationRefused(httpStatus);
    }

    @Entao("o histórico da OS registra {string}")
    public void theHistoryRecords(String statuses) {
        driver().assertHistory(Arrays.stream(statuses.split(",")).map(String::trim).toList());
    }

    @Entao("o estorno fica pendente e visível")
    public void theRefundStaysPending() {
        driver().assertRefundPending();
    }

    @Entao("o pedido de diagnóstico espera na fila do Execution")
    public void theDiagnosisRequestWaits() {
        driver().assertDiagnosisRequestWaiting();
    }

    @Entao("o mecânico é avisado de qual item não existe")
    public void theMechanicIsToldWhichItemIsUnknown() {
        driver().assertUnknownItemNamed();
    }
}
