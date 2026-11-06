package br.com.fiap.postech.carworkshop.bdd.e2e;

import br.com.fiap.postech.carworkshop.bdd.SagaDriver;
import br.com.fiap.postech.carworkshop.bdd.e2e.ClusterControl.Workload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import software.amazon.awssdk.services.sqs.model.Message;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EksSagaDriver implements SagaDriver {

    private static final Duration SAGA_STEP = Duration.ofMinutes(2);
    private static final Duration SERVICE_RETURN = Duration.ofMinutes(5);
    private static final Duration REFUND_REACHES_THE_DLQ = Duration.ofMinutes(10);
    private static final Duration POLL = Duration.ofSeconds(3);

    private static final String OS = "/carworkshop/v1";
    private static final String EXECUTION_QUEUE = "/execution/v1/queue";
    private static final String EXECUTION_WORK_ORDERS = "/execution/v1/work-orders/{id}/";
    private static final String BUDGETS = "/billing/v1/budgets";
    private static final String WORK_ORDER_TRACKING = OS + "/tracking/{id}";

    private static final String NO_BUDGET = "inexistente";
    private static final long SERVICE = 1L;
    private static final long UNKNOWN_ITEM = 999_999L;
    private static final int STOCK = 10;
    private static final int PARTS_PER_DIAGNOSIS = 2;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final GatewayCalls gateway;
    private final ClusterControl cluster = new ClusterControl();
    private final SagaQueues queues = new SagaQueues();
    private final String adminToken;

    private boolean withParts = true;
    private int stock = STOCK;
    private int partsPerDiagnosis = PARTS_PER_DIAGNOSIS;
    private String branchId;
    private String customerToken;
    private Long workOrderId;
    private long partId;
    private JsonPath unknownItemAnswer;
    private int mechanicAnswer;

    EksSagaDriver(String correlationId) {
        this.gateway = new GatewayCalls(E2eEnvironment.required("E2E_GATEWAY_URL"), correlationId);
        this.adminToken = gateway.anonymous()
                .body(Map.of("username", E2eEnvironment.required("E2E_ADMIN_USERNAME"),
                        "password", E2eEnvironment.required("E2E_ADMIN_PASSWORD")))
                .when().post(OS + "/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    void bringEverythingBack() {
        cluster.bringEverythingBack();
    }

    void takeOsDown() {
        cluster.takeDown(os());
    }

    void bringOsBack() {
        cluster.bringBack(os());
    }

    void tryToCompleteDiagnosis() {
        mechanicAnswer = diagnosisCompletion().statusCode();
    }

    void assertMechanicAnswer(int status) {
        assertEquals(status, mechanicAnswer);
    }

    private static Workload os() {
        return Workload.named("E2E_OS_NAMESPACE", "E2E_OS_DEPLOYMENT");
    }

    private static Workload execution() {
        return Workload.named("E2E_EXECUTION_NAMESPACE", "E2E_EXECUTION_DEPLOYMENT");
    }

    private static void eventually(Duration limit, ThrowingRunnable assertion) {
        Awaitility.await()
                .atMost(limit)
                .pollDelay(Duration.ZERO)
                .pollInterval(POLL)
                .ignoreExceptions()
                .untilAsserted(assertion);
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
    public void openWorkOrder(String branch) {
        branchId = E2eEnvironment.orDefault("E2E_BRANCH", branch);
        String cpf = randomCpf();
        long customerId = gateway.bearing(adminToken)
                .body(Map.of("name", "Cliente e2e", "document", cpf, "rg", "123456789",
                        "email", "e2e-" + cpf + "@exemplo.com", "number", "11999990000"))
                .when().post(OS + "/customers/create")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
        customerToken = gateway.anonymous()
                .body(Map.of("cpf", cpf))
                .when().post("/auth")
                .then().statusCode(200)
                .extract().path("access_token");
        long vehicleId = gateway.bearing(adminToken)
                .body(Map.of("vehiclePlate", randomPlate(), "manufacturer", "Fiat", "modelName", "Uno",
                        "modelYear", 2020, "customerId", customerId))
                .when().post(OS + "/vehicles/create")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
        partId = createPart();
        workOrderId = gateway.bearing(adminToken)
                .body(Map.of("customer_id", customerId, "vehicle_id", vehicleId, "branch_id", branchId,
                        "complaint", "Barulho no motor"))
                .when().post(OS + "/work-orders")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
        gateway.print("OS " + workOrderId + " aberta na filial " + branchId + ", peca " + partId);
    }

    private long createPart() {
        String code = "E2E-" + UUID.randomUUID();
        return gateway.bearing(adminToken)
                .body(Map.of("code", code, "manufacturer", "E2E", "description", "Peca do cenario " + code,
                        "price", 35.90, "type", "UNITARY", "quantity", stock))
                .when().post(OS + "/parts-and-supplies")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private void mechanicCalls(String action, Duration limit, Map<String, Object> body) {
        eventually(limit, () -> gateway.bearing(adminToken)
                .body(body)
                .when().post(EXECUTION_WORK_ORDERS + action, workOrderId)
                .then().statusCode(202));
    }

    @Override
    public void startDiagnosis() {
        mechanicCalls("diagnosis/start", SAGA_STEP, Map.of());
    }

    @Override
    public void completeDiagnosis() {
        eventually(SERVICE_RETURN, () -> assertEquals(202, diagnosisCompletion().statusCode()));
    }

    private Response diagnosisCompletion() {
        return gateway.bearing(adminToken)
                .body(Map.of("description", "Diagnostico do cenario", "items", diagnosedItems()))
                .when().post(EXECUTION_WORK_ORDERS + "diagnosis/complete", workOrderId);
    }

    private List<Map<String, Object>> diagnosedItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("type", "SERVICE", "catalogId", SERVICE, "quantity", 1));
        if (withParts) {
            items.add(Map.of("type", "PART", "catalogId", partId, "quantity", partsPerDiagnosis));
        }
        return items;
    }

    @Override
    public void completeDiagnosisWithUnknownItem() {
        unknownItemAnswer = gateway.bearing(adminToken)
                .body(Map.of("description", "Diagnostico do cenario",
                        "items", List.of(Map.of("type", "PART", "catalogId", UNKNOWN_ITEM, "quantity", 1))))
                .when().post(EXECUTION_WORK_ORDERS + "diagnosis/complete", workOrderId)
                .then().statusCode(400)
                .extract().jsonPath();
    }

    @Override
    public void failDiagnosis() {
        mechanicCalls("diagnosis/fail", SAGA_STEP, Map.of("reason", "sem reparo viavel"));
    }

    @Override
    public void approveBudget() {
        customerAnswersTheBudget("approve");
    }

    @Override
    public void rejectBudget() {
        customerAnswersTheBudget("reject");
    }

    private void customerAnswersTheBudget(String answer) {
        String budgetId = budget().getString("id");
        gateway.bearing(customerToken)
                .when().post(BUDGETS + "/{id}/" + answer, budgetId)
                .then().statusCode(202);
    }

    private JsonPath budget() {
        return budgetCall().then().statusCode(200).extract().jsonPath();
    }

    private Response budgetCall() {
        return gateway.bearing(adminToken)
                .queryParam("work_order_id", workOrderId)
                .when().get(BUDGETS);
    }

    @Override
    public void pay() {
        JsonPath charge = chargeWithItsLink();
        Instant deadline = Instant.parse(charge.getString("paymentDeadline"));
        gateway.notice("Pague agora, antes de " + deadline + ": " + charge.getString("paymentLink"));
        eventually(Duration.between(Instant.now(), deadline), () -> assertPaymentStatusNow("CONFIRMED"));
    }

    private JsonPath chargeWithItsLink() {
        JsonPath[] charge = new JsonPath[1];
        eventually(SAGA_STEP, () -> {
            charge[0] = budget();
            assertNotNull(charge[0].getString("paymentLink"));
        });
        return charge[0];
    }

    @Override
    public void startRepair() {
        mechanicCalls("repair/start", SAGA_STEP, Map.of());
    }

    @Override
    public void completeRepair() {
        mechanicCalls("repair/complete", SAGA_STEP, Map.of());
    }

    @Override
    public void failRepair() {
        mechanicCalls("repair/fail", SAGA_STEP, Map.of("reason", "peca nao serviu"));
    }

    @Override
    public void cancelWorkOrder() {
        gateway.bearing(adminToken)
                .when().post(OS + "/work-orders/{id}/cancel", workOrderId)
                .then().statusCode(200);
    }

    @Override
    public void deliverWorkOrder() {
        gateway.bearing(adminToken)
                .when().patch(OS + "/work-orders/{id}/deliver", workOrderId)
                .then().statusCode(200);
    }

    @Override
    public void expireApprovalDeadline() {
        waitForTheDeadline("E2E_APPROVAL_WAIT_SECONDS");
    }

    @Override
    public void expirePaymentDeadline() {
        JsonPath charge = chargeWithItsLink();
        gateway.notice("Para recusar antes do prazo, pague com FUND ate " + charge.getString("paymentDeadline")
                + ": " + charge.getString("paymentLink"));
        waitForTheDeadline("E2E_PAYMENT_WAIT_SECONDS");
    }

    private void waitForTheDeadline(String waitVariable) {
        Duration wait = E2eEnvironment.seconds(waitVariable);
        gateway.print("espera real pelo prazo: ate " + wait.toSeconds() + " s");
        eventually(wait, () -> assertWorkOrderStatusNow("CANCELED"));
    }

    @Override
    public void refuseRefunds() {
        gateway.print("a sandbox do provedor recusa todo estorno (ADR-008): nada a preparar");
    }

    @Override
    public void takeExecutionDown() {
        cluster.takeDown(execution());
    }

    @Override
    public void bringExecutionBack() {
        cluster.bringBack(execution());
    }

    private JsonPath tracking() {
        return gateway.bearing(adminToken)
                .when().get(WORK_ORDER_TRACKING, workOrderId)
                .then().statusCode(200)
                .extract().jsonPath();
    }

    @Override
    public void assertWorkOrderStatus(String status) {
        eventually(SAGA_STEP, () -> assertWorkOrderStatusNow(status));
    }

    private void assertWorkOrderStatusNow(String status) {
        assertEquals(status, tracking().getString("status"));
    }

    @Override
    public void assertWorkOrderCanceled(String status, String reason) {
        eventually(SAGA_STEP, () -> {
            JsonPath answer = tracking();
            assertEquals(status, answer.getString("status"));
            assertEquals(reason, answer.getString("cancel_reason"));
        });
    }

    @Override
    public void assertBudgetStatus(String status) {
        if (NO_BUDGET.equals(status)) {
            budgetCall().then().statusCode(404);
            return;
        }
        eventually(SAGA_STEP, () -> assertEquals(status, budget().getString("status")));
    }

    @Override
    public void assertPaymentStatus(String status) {
        eventually(SAGA_STEP, () -> assertPaymentStatusNow(status));
    }

    private void assertPaymentStatusNow(String status) {
        assertEquals(status, budget().getString("paymentStatus"));
    }

    @Override
    public void assertExecutionStatus(String status) {
        eventually(SAGA_STEP, () -> assertTrue(executionQueue(status).contains(workOrderId),
                "OS " + workOrderId + " fora da fila " + status + " da filial " + branchId));
    }

    private List<Long> executionQueue(String status) {
        return gateway.bearing(adminToken)
                .queryParam("branchId", branchId)
                .queryParam("status", status)
                .queryParam("limit", 100)
                .when().get(EXECUTION_QUEUE)
                .then().statusCode(200)
                .extract().jsonPath().getList("workOrderId", Long.class);
    }

    @Override
    public void assertPartStock(int quantity, int reserved) {
        eventually(SAGA_STEP, () -> {
            JsonPath part = gateway.bearing(adminToken)
                    .when().get(OS + "/parts-and-supplies/{id}", partId)
                    .then().statusCode(200)
                    .extract().jsonPath();
            assertEquals(quantity, part.getInt("quantity"));
            assertEquals(reserved, part.getInt("reserved_quantity"));
        });
    }

    @Override
    public void assertCancellationRefused(int httpStatus) {
        gateway.bearing(adminToken)
                .when().post(OS + "/work-orders/{id}/cancel", workOrderId)
                .then().statusCode(httpStatus);
    }

    @Override
    public void assertHistory(List<String> statuses) {
        eventually(SAGA_STEP, () -> assertEquals(statuses, tracking().getList("history.status", String.class)));
    }

    @Override
    public void assertRefundPending() {
        String deadLetterQueue = E2eEnvironment.required("E2E_BILLING_DLQ_URL");
        Message[] refund = new Message[1];
        gateway.print("esperando o RefundPayment esgotar as tentativas e cair na DLQ do Billing");
        eventually(REFUND_REACHES_THE_DLQ, () -> {
            refund[0] = queues.peek(deadLetterQueue)
                    .filter(isTheRefundOf(workOrderId))
                    .orElseThrow(() -> new AssertionError("RefundPayment ainda fora da DLQ do Billing"));
        });
        gateway.print("evidencia, envelope na DLQ: " + refund[0].body());
    }

    private static Predicate<Message> isTheRefundOf(long workOrderId) {
        return message -> {
            JsonNode envelope = parse(message.body());
            return "RefundPayment".equals(envelope.path("type").asText())
                    && envelope.path("workOrderId").asLong() == workOrderId;
        };
    }

    private static JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corpo de mensagem invalido na DLQ: " + body, e);
        }
    }

    @Override
    public void assertDiagnosisRequestWaiting() {
        String queue = E2eEnvironment.required("E2E_EXECUTION_COMMANDS_QUEUE_URL");
        eventually(SAGA_STEP, () -> assertTrue(queues.waitingOn(queue) >= 1,
                "StartDiagnosis devia esperar na fila do Execution"));
    }

    @Override
    public void assertUnknownItemNamed() {
        assertTrue(unknownItemAnswer.getString("error").contains(String.valueOf(UNKNOWN_ITEM)),
                "a resposta nao nomeia o item: " + unknownItemAnswer.prettify());
    }

    private static String randomCpf() {
        int[] digits = new int[11];
        for (int position = 0; position < 9; position++) {
            digits[position] = RANDOM.nextInt(10);
        }
        digits[9] = checkDigit(digits, 9);
        digits[10] = checkDigit(digits, 10);
        StringBuilder cpf = new StringBuilder();
        for (int digit : digits) {
            cpf.append(digit);
        }
        return cpf.toString();
    }

    private static int checkDigit(int[] digits, int length) {
        int sum = 0;
        for (int position = 0; position < length; position++) {
            sum += digits[position] * (length + 1 - position);
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    private static String randomPlate() {
        return "" + randomLetter() + randomLetter() + randomLetter() + RANDOM.nextInt(10) + randomLetter()
                + RANDOM.nextInt(10) + RANDOM.nextInt(10);
    }

    private static char randomLetter() {
        return (char) ('A' + RANDOM.nextInt(26));
    }
}
