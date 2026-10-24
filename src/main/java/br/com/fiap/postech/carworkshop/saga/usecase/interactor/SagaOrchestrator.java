package br.com.fiap.postech.carworkshop.saga.usecase.interactor;

import br.com.fiap.postech.carworkshop.saga.domain.entity.CompensationStep;
import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaInstance;
import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaState;
import br.com.fiap.postech.carworkshop.saga.domain.message.CancelBudgetPayload;
import br.com.fiap.postech.carworkshop.saga.domain.message.CancelDiagnosisPayload;
import br.com.fiap.postech.carworkshop.saga.domain.message.CancelPaymentPayload;
import br.com.fiap.postech.carworkshop.saga.domain.message.CreateBudgetPayload;
import br.com.fiap.postech.carworkshop.saga.domain.message.RefundPaymentPayload;
import br.com.fiap.postech.carworkshop.saga.domain.message.RequestPaymentPayload;
import br.com.fiap.postech.carworkshop.saga.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.saga.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.saga.domain.message.SagaPayload;
import br.com.fiap.postech.carworkshop.saga.domain.message.StartDiagnosisPayload;
import br.com.fiap.postech.carworkshop.saga.domain.message.StartRepairPayload;
import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaOrchestratorUseCase;
import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaReply;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.PartsReservationPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaDeadlinePort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaInboxPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaInstanceRepositoryPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaOutboxPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.WorkOrderProjectionPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.WorkOrderProjectionPort.WorkOrderSnapshot;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.shared.domain.exception.EntityNotFoundException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.StockException;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
public class SagaOrchestrator implements SagaOrchestratorUseCase {

    private final SagaInstanceRepositoryPort instances;
    private final SagaOutboxPort outbox;
    private final SagaInboxPort inbox;
    private final WorkOrderProjectionPort workOrders;
    private final PartsReservationPort parts;
    private final SagaDeadlinePort deadlines;

    public SagaOrchestrator(SagaInstanceRepositoryPort instances,
                            SagaOutboxPort outbox,
                            SagaInboxPort inbox,
                            WorkOrderProjectionPort workOrders,
                            PartsReservationPort parts,
                            SagaDeadlinePort deadlines) {
        this.instances = instances;
        this.outbox = outbox;
        this.inbox = inbox;
        this.workOrders = workOrders;
        this.parts = parts;
        this.deadlines = deadlines;
    }

    @Override
    public void start(Long workOrderId, String branchId, String vehiclePlate, String complaint,
                      String correlationId) {
        SagaInstance saga = instances.save(SagaInstance.start(workOrderId, branchId));
        send(saga, SagaMessageType.START_DIAGNOSIS,
                new StartDiagnosisPayload(vehiclePlate, complaint), correlationId, null);
        log.info("Saga {} started for Work Order {}.", saga.getSagaId(), workOrderId);
    }

    @Override
    public void onReply(SagaReply reply) {
        SagaInstance saga = instances.findByWorkOrderId(reply.workOrderId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "No saga for Work Order " + reply.workOrderId() + "."));

        if (!inbox.recordIfNew(reply.messageId(), reply.workOrderId(), reply.type())) {
            log.info("Reply {} of type {} was already processed: nothing to do.",
                    reply.messageId(), reply.type());
            return;
        }

        dispatch(saga, reply);
        instances.save(saga);
    }

    @Override
    public void cancel(Long workOrderId, CancelReason reason) {
        SagaInstance saga = instances.findByWorkOrderId(workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("No saga for Work Order " + workOrderId + "."));
        compensate(saga, reason, null, null);
        instances.save(saga);
    }

    @Override
    public void expireDeadlines(int batchSize) {
        for (SagaInstance saga : instances.claimExpired(Instant.now(), batchSize)) {
            CancelReason reason = saga.isWaitingFor(SagaState.AWAITING_PAYMENT)
                    ? CancelReason.PAYMENT_EXPIRED
                    : CancelReason.APPROVAL_EXPIRED;
            log.info("Saga {} expired while in {}: compensating with {}.",
                    saga.getSagaId(), saga.getState(), reason);
            compensate(saga, reason, null, null);
            instances.save(saga);
        }
    }

    private void dispatch(SagaInstance saga, SagaReply reply) {
        switch (reply.type()) {
            case SagaMessageType.DIAGNOSIS_STARTED -> onDiagnosisStarted(saga);
            case SagaMessageType.DIAGNOSIS_COMPLETED -> onDiagnosisCompleted(saga, reply);
            case SagaMessageType.BUDGET_CREATED -> onBudgetCreated(saga, reply);
            case SagaMessageType.BUDGET_APPROVED -> onBudgetApproved(saga, reply);
            case SagaMessageType.PAYMENT_REQUESTED -> onPaymentRequested(saga);
            case SagaMessageType.PAYMENT_CONFIRMED -> onPaymentConfirmed(saga, reply);
            case SagaMessageType.REPAIR_STARTED -> saga.advanceTo(SagaState.REPAIRING);
            case SagaMessageType.REPAIR_COMPLETED -> onRepairCompleted(saga);
            case SagaMessageType.DIAGNOSIS_FAILED -> compensate(saga, CancelReason.DIAGNOSIS_FAILED, reply);
            case SagaMessageType.BUDGET_CREATION_FAILED ->
                    compensate(saga, CancelReason.BUDGET_CREATION_FAILED, reply);
            case SagaMessageType.BUDGET_REJECTED -> compensate(saga, CancelReason.REJECTED_BY_CUSTOMER, reply);
            case SagaMessageType.PAYMENT_REQUEST_FAILED ->
                    compensate(saga, CancelReason.PAYMENT_REQUEST_FAILED, reply);
            case SagaMessageType.REPAIR_FAILED -> compensate(saga, CancelReason.REPAIR_FAILED, reply);
            case SagaMessageType.DIAGNOSIS_CANCELED ->
                    confirmCompensation(saga, CompensationStep.CANCEL_DIAGNOSIS);
            case SagaMessageType.BUDGET_CANCELED ->
                    confirmCompensation(saga, CompensationStep.CANCEL_BUDGET);
            case SagaMessageType.PAYMENT_CANCELED ->
                    confirmCompensation(saga, CompensationStep.CANCEL_PAYMENT);
            case SagaMessageType.PAYMENT_REFUNDED ->
                    confirmCompensation(saga, CompensationStep.REFUND_PAYMENT);
            case SagaMessageType.PAYMENT_ATTEMPT_REJECTED ->
                    log.info("Saga {} had a payment attempt rejected ({}): still waiting for the deadline.",
                            saga.getSagaId(), reply.reason());
            default -> log.warn("Saga {} received the unknown reply type {}: ignored.",
                    saga.getSagaId(), reply.type());
        }
    }

    private void onDiagnosisStarted(SagaInstance saga) {
        if (saga.advanceTo(SagaState.DIAGNOSING)) {
            workOrders.markUnderDiagnosis(saga.getWorkOrderId(), saga.getSagaId());
        }
    }

    private void onDiagnosisCompleted(SagaInstance saga, SagaReply reply) {
        if (!saga.advanceTo(SagaState.AWAITING_BUDGET)) {
            return;
        }
        workOrders.recordPricedDiagnosis(saga.getWorkOrderId(), saga.getSagaId(),
                reply.description(), reply.items());
        WorkOrderSnapshot snapshot = workOrders.snapshotOf(saga.getWorkOrderId());
        send(saga, SagaMessageType.CREATE_BUDGET,
                new CreateBudgetPayload(snapshot.customerDocument(), snapshot.items(), snapshot.total(),
                        deadlines.approvalDeadline()),
                reply);
    }

    private void onBudgetCreated(SagaInstance saga, SagaReply reply) {
        saga.rememberBudget(reply.budgetId());
        if (saga.advanceTo(SagaState.AWAITING_APPROVAL, deadlines.approvalDeadline())) {
            workOrders.markPendingApproval(saga.getWorkOrderId(), saga.getSagaId());
        }
    }

    private void onBudgetApproved(SagaInstance saga, SagaReply reply) {
        if (reply.budgetId() != null) {
            saga.rememberBudget(reply.budgetId());
        }
        if (!saga.isWaitingFor(SagaState.AWAITING_APPROVAL)) {
            return;
        }
        try {
            parts.reserve(saga.getWorkOrderId());
        } catch (StockException e) {
            // Estoque insuficiente e regra de negocio, nao falha transitoria: compensa em vez de reentregar
            // a mensagem ate a DLQ. A reserva valida tudo antes de aplicar, entao nada ficou pela metade.
            log.warn("Saga {} could not reserve the parts ({}): compensating.", saga.getSagaId(), e.getMessage());
            compensate(saga, CancelReason.OUT_OF_STOCK, reply);
            return;
        }
        saga.advanceTo(SagaState.AWAITING_PAYMENT_LINK);
        workOrders.markAwaitingPayment(saga.getWorkOrderId(), saga.getSagaId());
        WorkOrderSnapshot snapshot = workOrders.snapshotOf(saga.getWorkOrderId());
        send(saga, SagaMessageType.REQUEST_PAYMENT,
                new RequestPaymentPayload(saga.getBudgetId(), snapshot.total(), deadlines.paymentDeadline()),
                reply);
    }

    private void onPaymentRequested(SagaInstance saga) {
        saga.advanceTo(SagaState.AWAITING_PAYMENT, deadlines.paymentDeadline());
    }

    private void onPaymentConfirmed(SagaInstance saga, SagaReply reply) {
        saga.rememberPayment(reply.paymentId());

        // Pagamento que chega depois da compensacao nao fica retido: estorna na hora, com motivo registrado.
        if (saga.getState().isCompensating()) {
            log.warn("Saga {} received a payment after compensating: refunding it.", saga.getSagaId());
            send(saga, SagaMessageType.REFUND_PAYMENT,
                    new RefundPaymentPayload(reply.paymentId(), "payment confirmed after compensation"),
                    reply);
            return;
        }
        if (!saga.isWaitingFor(SagaState.AWAITING_PAYMENT)
                && !saga.isWaitingFor(SagaState.AWAITING_PAYMENT_LINK)) {
            return;
        }
        parts.confirm(saga.getWorkOrderId());
        saga.advanceTo(SagaState.AWAITING_REPAIR);
        workOrders.markInProgress(saga.getWorkOrderId(), saga.getSagaId());
        WorkOrderSnapshot snapshot = workOrders.snapshotOf(saga.getWorkOrderId());
        send(saga, SagaMessageType.START_REPAIR, new StartRepairPayload(snapshot.items()), reply);
    }

    private void onRepairCompleted(SagaInstance saga) {
        if (saga.advanceTo(SagaState.COMPLETED)) {
            workOrders.markCompleted(saga.getWorkOrderId(), saga.getSagaId());
        }
    }

    private void compensate(SagaInstance saga, CancelReason reason, SagaReply cause) {
        compensate(saga, reason, cause == null ? null : cause.correlationId(),
                cause == null ? null : cause.messageId());
    }

    private void compensate(SagaInstance saga, CancelReason reason, String correlationId, UUID causationId) {
        List<CompensationStep> steps = saga.beginCompensation(reason);
        if (steps.isEmpty() && !saga.isCompensationComplete()) {
            return;
        }
        for (CompensationStep step : steps) {
            applyCompensation(saga, step, reason, correlationId, causationId);
        }
        finishIfComplete(saga);
    }

    private void applyCompensation(SagaInstance saga, CompensationStep step, CancelReason reason,
                                   String correlationId, UUID causationId) {
        switch (step) {
            case RELEASE_PARTS -> parts.release(saga.getWorkOrderId());
            case RESTORE_PARTS -> parts.restore(saga.getWorkOrderId());
            case REFUND_PAYMENT -> send(saga, SagaMessageType.REFUND_PAYMENT,
                    new RefundPaymentPayload(saga.getPaymentId(), reason.name()), correlationId, causationId);
            case CANCEL_PAYMENT -> send(saga, SagaMessageType.CANCEL_PAYMENT,
                    new CancelPaymentPayload(saga.getBudgetId(), reason.name()), correlationId, causationId);
            case CANCEL_BUDGET -> send(saga, SagaMessageType.CANCEL_BUDGET,
                    new CancelBudgetPayload(saga.getBudgetId(), reason.name()), correlationId, causationId);
            case CANCEL_DIAGNOSIS -> send(saga, SagaMessageType.CANCEL_DIAGNOSIS,
                    new CancelDiagnosisPayload(reason.name()), correlationId, causationId);
        }
    }

    private void confirmCompensation(SagaInstance saga, CompensationStep step) {
        if (!saga.confirmCompensation(step)) {
            log.info("Saga {} was not waiting for {}: ignored.", saga.getSagaId(), step);
            return;
        }
        finishIfComplete(saga);
    }

    // A OS so fica CANCELED depois que todas as compensacoes foram confirmadas: enquanto falta uma, o cliente
    // nao pode ver "cancelado" com o dinheiro ainda retido.
    private void finishIfComplete(SagaInstance saga) {
        if (saga.finishCompensation()) {
            workOrders.markCanceled(saga.getWorkOrderId(), saga.getSagaId(), saga.getCancelReason());
            log.info("Saga {} is compensated ({}).", saga.getSagaId(), saga.getCancelReason());
        }
    }

    private void send(SagaInstance saga, String type, SagaPayload payload, SagaReply cause) {
        send(saga, type, payload, cause.correlationId(), cause.messageId());
    }

    private void send(SagaInstance saga, String type, SagaPayload payload,
                      String correlationId, UUID causationId) {
        outbox.append(SagaEnvelope.command(type, saga.getWorkOrderId(), saga.getSagaId(),
                saga.getBranchId(), correlationId, causationId, payload));
    }
}
