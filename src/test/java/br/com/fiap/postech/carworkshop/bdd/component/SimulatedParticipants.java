package br.com.fiap.postech.carworkshop.bdd.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.awssdk.services.sqs.model.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class SimulatedParticipants {

    static final String NO_BUDGET = "inexistente";
    private static final String NOTHING_YET = "nenhum";
    private static final Set<String> EXECUTION_FINAL_STATES =
            Set.of("DIAGNOSIS_FAILED", "REPAIR_FAILED", "REPAIR_DONE", "CANCELED");

    private final SagaBench bench;
    private final String correlationId;
    private final List<String> executionCommands = new ArrayList<>();
    private final List<String> billingCommands = new ArrayList<>();

    private long workOrderId;
    private boolean executionDown;
    private boolean holdingCompensations;
    private boolean refusingBudgets;
    private boolean failingPaymentRequests;
    private boolean refusingRefunds;

    private String executionStatus = NOTHING_YET;
    private String budgetStatus = NO_BUDGET;
    private String paymentStatus = NOTHING_YET;
    private String budgetId;
    private String lastReply;

    SimulatedParticipants(SagaBench bench, String correlationId) {
        this.bench = bench;
        this.correlationId = correlationId;
    }

    void follow(long workOrderId) {
        this.workOrderId = workOrderId;
    }

    void takeExecutionDown() {
        executionDown = true;
    }

    void bringExecutionBack() {
        executionDown = false;
    }

    void holdCompensations() {
        holdingCompensations = true;
    }

    void releaseCompensations() {
        holdingCompensations = false;
    }

    void refuseBudgets() {
        refusingBudgets = true;
    }

    void failPaymentRequests() {
        failingPaymentRequests = true;
    }

    void refuseRefunds() {
        refusingRefunds = true;
    }

    String executionStatus() {
        return executionStatus;
    }

    String budgetStatus() {
        return budgetStatus;
    }

    String paymentStatus() {
        return paymentStatus;
    }

    String lastReply() {
        return lastReply;
    }

    UUID lastReplyId() {
        return UUID.fromString(bench.parse(lastReply).path("messageId").asText());
    }

    List<String> executionCommands() {
        return executionCommands;
    }

    List<String> billingCommands() {
        return billingCommands;
    }

    int answerCommands() {
        int answered = 0;
        if (!executionDown && !holdingCompensations) {
            answered += answer(bench.executionQueueUrl(), true);
        }
        if (!holdingCompensations) {
            answered += answer(bench.billingQueueUrl(), false);
        }
        return answered;
    }

    private int answer(String queueUrl, boolean execution) {
        List<Message> messages = bench.receiveCommands(queueUrl);
        for (Message message : messages) {
            JsonNode command = bench.parse(message.body());
            bench.delete(queueUrl, message);
            if (command.path("workOrderId").asLong() != workOrderId) {
                continue;
            }
            String type = command.path("type").asText();
            if (execution) {
                executionCommands.add(type);
                onExecutionCommand(type);
            } else {
                billingCommands.add(type);
                onBillingCommand(type, command);
            }
        }
        return messages.size();
    }

    private void onExecutionCommand(String type) {
        switch (type) {
            case "StartDiagnosis" -> executionStatus = "DIAGNOSIS_QUEUED";
            case "StartRepair" -> executionStatus = "REPAIR_QUEUED";
            case "CancelDiagnosis" -> {
                if (!EXECUTION_FINAL_STATES.contains(executionStatus)) {
                    executionStatus = "CANCELED";
                }
                reply("DiagnosisCanceled", payload());
            }
            default -> throw new IllegalStateException("Comando inesperado no Execution: " + type);
        }
    }

    private void onBillingCommand(String type, JsonNode command) {
        switch (type) {
            case "CreateBudget" -> onCreateBudget();
            case "RequestPayment" -> onRequestPayment();
            case "CancelBudget" -> {
                budgetStatus = "CANCELED";
                reply("BudgetCanceled", payload().put("budgetId", budgetId));
            }
            case "CancelPayment" -> {
                paymentStatus = "CANCELED";
                reply("PaymentCanceled", payload().put("budgetId", budgetId));
            }
            case "RefundPayment" -> onRefundPayment(command);
            default -> throw new IllegalStateException("Comando inesperado no Billing: " + type);
        }
    }

    private void onCreateBudget() {
        if (refusingBudgets) {
            reply("BudgetCreationFailed", payload().put("reason", "regra de negocio do Billing"));
            return;
        }
        budgetId = UUID.randomUUID().toString();
        budgetStatus = "PENDING_APPROVAL";
        reply("BudgetCreated", payload().put("budgetId", budgetId));
    }

    private void onRequestPayment() {
        if (failingPaymentRequests) {
            reply("PaymentRequestFailed", payload().put("reason", "5xx do provedor esgotou a retentativa"));
            return;
        }
        paymentStatus = "PENDING";
        reply("PaymentRequested", payload().put("budgetId", budgetId));
    }

    private void onRefundPayment(JsonNode command) {
        if (refusingRefunds) {
            return;
        }
        paymentStatus = "REFUNDED";
        reply("PaymentRefunded", payload().put("paymentId", command.path("payload").path("paymentId").asText()));
    }

    void diagnosisStarted() {
        executionStatus = "DIAGNOSIS_IN_PROGRESS";
        reply("DiagnosisStarted", payload().put("mechanic", "mecanico"));
    }

    void diagnosisCompleted(ObjectNode payload) {
        executionStatus = "DIAGNOSIS_DONE";
        reply("DiagnosisCompleted", payload);
    }

    void diagnosisFailed() {
        executionStatus = "DIAGNOSIS_FAILED";
        reply("DiagnosisFailed", payload().put("reason", "sem reparo viavel"));
    }

    void budgetApproved() {
        budgetStatus = "APPROVED";
        reply("BudgetApproved", payload().put("budgetId", budgetId));
    }

    void budgetRejected() {
        budgetStatus = "REJECTED";
        reply("BudgetRejected", payload().put("budgetId", budgetId));
    }

    void paymentConfirmed(String paymentId) {
        bench.sendReply(paymentConfirmedEnvelope(paymentId), workOrderId);
    }

    String paymentConfirmedEnvelope(String paymentId) {
        paymentStatus = "CONFIRMED";
        lastReply = envelope("PaymentConfirmed", payload().put("paymentId", paymentId), workOrderId);
        return lastReply;
    }

    void repairStarted() {
        executionStatus = "REPAIR_IN_PROGRESS";
        reply("RepairStarted", payload());
    }

    void repairCompleted() {
        executionStatus = "REPAIR_DONE";
        reply("RepairCompleted", payload());
    }

    void repairFailed() {
        executionStatus = "REPAIR_FAILED";
        reply("RepairFailed", payload().put("reason", "peca nao serviu"));
    }

    ObjectNode payload() {
        return bench.newObject();
    }

    private void reply(String type, ObjectNode payload) {
        String envelope = envelope(type, payload, workOrderId);
        bench.sendReply(envelope, workOrderId);
        lastReply = envelope;
    }

    String envelope(String type, ObjectNode payload, long targetWorkOrder) {
        return bench.newObject()
                .put("messageId", UUID.randomUUID().toString())
                .put("type", type)
                .put("version", 1)
                .put("occurredAt", Instant.now().toString())
                .put("sagaId", UUID.randomUUID().toString())
                .put("workOrderId", targetWorkOrder)
                .put("correlationId", correlationId)
                .set("payload", payload)
                .toString();
    }
}
