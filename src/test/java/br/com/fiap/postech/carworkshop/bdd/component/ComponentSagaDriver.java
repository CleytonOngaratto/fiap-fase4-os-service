package br.com.fiap.postech.carworkshop.bdd.component;

import br.com.fiap.postech.carworkshop.bdd.SagaDriver;
import br.com.fiap.postech.carworkshop.utils.TokenUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentSagaDriver implements SagaDriver {

    private static final int SETTLE_LIMIT = 25;
    private static final long CUSTOMER = 1L;
    private static final long VEHICLE = 1L;
    private static final long SERVICE = 1L;
    private static final long UNKNOWN_ITEM = 999_999L;
    private static final int STOCK = 10;
    private static final int PARTS_PER_DIAGNOSIS = 2;

    private static final Map<String, List<String>> WORK_ORDER_STATUS_BY_SAGA_STATE = Map.of(
            "AWAITING_DIAGNOSIS", List.of("RECEIVED"),
            "DIAGNOSING", List.of("UNDER_DIAGNOSIS"),
            "AWAITING_BUDGET", List.of("UNDER_DIAGNOSIS"),
            "AWAITING_APPROVAL", List.of("PENDING_APPROVAL"),
            "AWAITING_PAYMENT_LINK", List.of("AWAITING_PAYMENT"),
            "AWAITING_PAYMENT", List.of("AWAITING_PAYMENT"),
            "AWAITING_REPAIR", List.of("IN_PROGRESS"),
            "REPAIRING", List.of("IN_PROGRESS"),
            "COMPLETED", List.of("COMPLETED", "DELIVERED"),
            "COMPENSATED", List.of("CANCELED"));

    private final SagaBench bench;
    private final String correlationId;
    private final SimulatedParticipants participants;

    private boolean withParts = true;
    private int stock = STOCK;
    private int partsPerDiagnosis = PARTS_PER_DIAGNOSIS;
    private Long workOrderId;
    private long partId;
    private JsonPath catalogAnswer;
    private boolean stuckCompensationExpected;
    private boolean deadLetterExpected;

    ComponentSagaDriver(SagaBench bench, String correlationId) {
        this.bench = bench;
        this.correlationId = correlationId;
        this.participants = new SimulatedParticipants(bench, correlationId);
        bench.purgeQueues();
    }

    SimulatedParticipants participants() {
        return participants;
    }

    SagaBench bench() {
        return bench;
    }

    long workOrderId() {
        return workOrderId;
    }

    void expectDeadLetter() {
        deadLetterExpected = true;
    }

    void settle() {
        for (int round = 0; round < SETTLE_LIMIT; round++) {
            bench.drainOutbox();
            int answered = participants.answerCommands();
            bench.pollReplies();
            if (answered == 0 && bench.pendingOutbox() == 0 && bench.visibleReplies() == 0) {
                return;
            }
        }
        throw new AssertionError("A saga nao aquietou em " + SETTLE_LIMIT + " rodadas: mensagens circulando sem parar.");
    }

    private RequestSpecification asAdmin() {
        return given()
                .header("Authorization", "Bearer " + TokenUtils.generateAdminToken())
                .header("X-Trace-Id", correlationId)
                .contentType("application/json");
    }

    @Override
    public void useOnlyServices() {
        withParts = false;
    }

    @Override
    public void usePartOutOfStock() {
        stock = 0;
        partsPerDiagnosis = 1;
    }

    @Override
    public void openWorkOrder(String branchId) {
        partId = createPart();
        workOrderId = asAdmin()
                .body(Map.of("customer_id", CUSTOMER, "vehicle_id", VEHICLE, "branch_id", branchId,
                        "complaint", "Barulho no motor"))
                .when().post("/work-orders")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
        participants.follow(workOrderId);
        settle();
    }

    private long createPart() {
        String code = "BDD-" + UUID.randomUUID();
        return asAdmin()
                .body(Map.of("code", code, "manufacturer", "BDD", "description", "Peca do cenario " + code,
                        "price", 35.90, "type", "UNITARY", "quantity", stock))
                .when().post("/parts-and-supplies")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    @Override
    public void startDiagnosis() {
        participants.diagnosisStarted();
        settle();
    }

    @Override
    public void completeDiagnosis() {
        ObjectNode payload = participants.payload().put("description", "Diagnostico do cenario");
        var items = payload.putArray("items");
        items.addObject().put("type", "SERVICE").put("catalogId", SERVICE).put("quantity", 1);
        if (withParts) {
            items.addObject().put("type", "PART").put("catalogId", partId).put("quantity", partsPerDiagnosis);
        }
        participants.diagnosisCompleted(payload);
        settle();
    }

    @Override
    public void completeDiagnosisWithUnknownItem() {
        catalogAnswer = given()
                .header("X-Service-Token", "test-service-token")
                .contentType("application/json")
                .body(Map.of("items", List.of(Map.of("type", "PART", "catalogId", UNKNOWN_ITEM))))
                .when().post("/internal/catalog/validate")
                .then().statusCode(200)
                .extract().jsonPath();
        settle();
    }

    @Override
    public void failDiagnosis() {
        participants.diagnosisFailed();
        settle();
    }

    @Override
    public void approveBudget() {
        participants.budgetApproved();
        settle();
    }

    @Override
    public void rejectBudget() {
        participants.budgetRejected();
        settle();
    }

    @Override
    public void pay() {
        participants.paymentConfirmed("provedor-" + workOrderId);
        settle();
    }

    @Override
    public void startRepair() {
        participants.repairStarted();
        settle();
    }

    @Override
    public void completeRepair() {
        participants.repairCompleted();
        settle();
    }

    @Override
    public void failRepair() {
        participants.repairFailed();
        settle();
    }

    @Override
    public void cancelWorkOrder() {
        asAdmin().when().post("/work-orders/{id}/cancel", workOrderId).then().statusCode(200);
        settle();
    }

    @Override
    public void deliverWorkOrder() {
        asAdmin().when().patch("/work-orders/{id}/deliver", workOrderId).then().statusCode(200);
        settle();
    }

    @Override
    public void expireApprovalDeadline() {
        expireDeadline();
    }

    @Override
    public void expirePaymentDeadline() {
        expireDeadline();
    }

    private void expireDeadline() {
        bench.rewindDeadline(workOrderId);
        bench.scanDeadlines();
        settle();
    }

    @Override
    public void refuseRefunds() {
        participants.refuseRefunds();
    }

    @Override
    public void takeExecutionDown() {
        participants.takeExecutionDown();
    }

    @Override
    public void bringExecutionBack() {
        participants.bringExecutionBack();
        settle();
    }

    private ValidatableResponse tracking() {
        return asAdmin().when().get("/tracking/{id}", workOrderId).then().statusCode(200);
    }

    @Override
    public void assertWorkOrderStatus(String status) {
        assertEquals(status, tracking().extract().jsonPath().getString("status"));
    }

    @Override
    public void assertWorkOrderCanceled(String status, String reason) {
        JsonPath answer = tracking().extract().jsonPath();
        assertEquals(status, answer.getString("status"));
        assertEquals(reason, answer.getString("cancel_reason"));
    }

    @Override
    public void assertBudgetStatus(String status) {
        assertEquals(status, participants.budgetStatus());
    }

    @Override
    public void assertPaymentStatus(String status) {
        assertEquals(status, participants.paymentStatus());
    }

    @Override
    public void assertExecutionStatus(String status) {
        assertEquals(status, participants.executionStatus());
    }

    @Override
    public void assertPartStock(int quantity, int reserved) {
        JsonPath part = asAdmin().when().get("/parts-and-supplies/{id}", partId)
                .then().statusCode(200).extract().jsonPath();
        assertEquals(quantity, part.getInt("quantity"));
        assertEquals(reserved, part.getInt("reserved_quantity"));
    }

    @Override
    public void assertCancellationRefused(int httpStatus) {
        asAdmin().when().post("/work-orders/{id}/cancel", workOrderId).then().statusCode(httpStatus);
    }

    @Override
    public void assertHistory(List<String> statuses) {
        assertEquals(statuses, tracking().extract().jsonPath().getList("history.status", String.class));
    }

    @Override
    public void assertRefundPending() {
        stuckCompensationExpected = true;
        assertEquals("COMPENSATING", bench.sagaState(workOrderId));
        assertEquals("REFUND_PAYMENT", bench.pendingCompensations(workOrderId));
    }

    @Override
    public void assertDiagnosisRequestWaiting() {
        settleWithoutTheExecution();
        assertTrue(bench.waitingOn(bench.executionQueueUrl()) >= 1, "StartDiagnosis devia esperar na fila do Execution");
    }

    private void settleWithoutTheExecution() {
        bench.drainOutbox();
    }

    @Override
    public void assertUnknownItemNamed() {
        assertEquals(List.of((int) UNKNOWN_ITEM), catalogAnswer.getList("invalid.catalogId", Integer.class));
    }

    void assertScenarioInvariants() {
        settle();
        assertEquals(0L, bench.pendingOutbox(), "nenhum comando pode ficar preso no outbox");
        if (!deadLetterExpected) {
            assertEquals(0, bench.deadLettered(), "nenhuma mensagem deste cenario podia parar numa DLQ");
        }
        if (workOrderId == null) {
            return;
        }
        String sagaState = bench.sagaState(workOrderId);
        String status = bench.workOrderStatus(workOrderId);
        if (stuckCompensationExpected) {
            assertEquals("COMPENSATING", sagaState);
            return;
        }
        assertTrue(List.of("COMPLETED", "COMPENSATED").contains(sagaState),
                "a saga terminou presa em " + sagaState);
        assertTrue(WORK_ORDER_STATUS_BY_SAGA_STATE.get(sagaState).contains(status),
                "saga em " + sagaState + " e OS em " + status + ": a projecao ficou para tras");
    }
}
