package br.com.fiap.postech.carworkshop.saga.usecase.interactor;

import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaInstance;
import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaState;
import br.com.fiap.postech.carworkshop.saga.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.saga.domain.message.SagaItem;
import br.com.fiap.postech.carworkshop.saga.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.saga.domain.message.RefundPaymentPayload;
import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaReply;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.PartsReservationPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaDeadlinePort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaInboxPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaInstanceRepositoryPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaOutboxPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.WorkOrderProjectionPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.WorkOrderProjectionPort.WorkOrderSnapshot;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ConflictException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.EntityNotFoundException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.StockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaOrchestratorTest {

    private static final Long WORK_ORDER = 42L;
    private static final String BRANCH = "SP-01";
    private static final String TRACE = "EVIDENCIA-B4-001";
    private static final String DOCUMENT = "12345678901";
    private static final Instant APPROVAL_DEADLINE = Instant.parse("2026-10-24T12:00:00Z");
    private static final Instant PAYMENT_DEADLINE = Instant.parse("2026-10-22T12:30:00Z");
    private static final List<SagaItem> ITEMS = List.of(
            new SagaItem(SagaItem.SERVICE, 1L, "Troca de oleo", 1, new BigDecimal("150.00")),
            new SagaItem(SagaItem.PART, 3L, "Filtro de oleo", 2, new BigDecimal("35.00")));

    private final SagaInstanceRepositoryPort instances = mock(SagaInstanceRepositoryPort.class);
    private final SagaOutboxPort outbox = mock(SagaOutboxPort.class);
    private final SagaInboxPort inbox = mock(SagaInboxPort.class);
    private final WorkOrderProjectionPort workOrders = mock(WorkOrderProjectionPort.class);
    private final PartsReservationPort parts = mock(PartsReservationPort.class);
    private final SagaDeadlinePort deadlines = mock(SagaDeadlinePort.class);

    private SagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new SagaOrchestrator(instances, outbox, inbox, workOrders, parts, deadlines);
        when(instances.save(any(SagaInstance.class))).thenAnswer(call -> call.getArgument(0));
        when(inbox.recordIfNew(any(), anyLong(), anyString())).thenReturn(true);
        when(deadlines.approvalDeadline()).thenReturn(APPROVAL_DEADLINE);
        when(deadlines.paymentDeadline()).thenReturn(PAYMENT_DEADLINE);
        when(workOrders.snapshotOf(WORK_ORDER)).thenReturn(new WorkOrderSnapshot(
                WORK_ORDER, BRANCH, DOCUMENT, ITEMS, new BigDecimal("220.00"), false));
    }

    private SagaInstance sagaAt(SagaState state) {
        SagaInstance saga = SagaInstance.start(WORK_ORDER, BRANCH);
        saga.setState(state);
        saga.setBudgetId("B-77");
        saga.setPaymentId("MP-9988");
        when(instances.findByWorkOrderId(WORK_ORDER)).thenReturn(Optional.of(saga));
        return saga;
    }

    private static SagaReply reply(String type) {
        return SagaReply.builder()
                .messageId(UUID.randomUUID())
                .type(type)
                .workOrderId(WORK_ORDER)
                .correlationId(TRACE)
                .build();
    }

    private List<SagaEnvelope> sentCommands() {
        ArgumentCaptor<SagaEnvelope> captor = ArgumentCaptor.forClass(SagaEnvelope.class);
        verify(outbox, org.mockito.Mockito.atLeastOnce()).append(captor.capture());
        return captor.getAllValues();
    }

    private SagaEnvelope onlyCommand() {
        List<SagaEnvelope> commands = sentCommands();
        assertEquals(1, commands.size());
        return commands.get(0);
    }

    @Test
    void start_createsTheSagaAndCommandsTheDiagnosis() {
        orchestrator.start(WORK_ORDER, BRANCH, "ABC-1234", "Barulho no motor", TRACE);

        ArgumentCaptor<SagaInstance> saga = ArgumentCaptor.forClass(SagaInstance.class);
        verify(instances).save(saga.capture());
        assertEquals(SagaState.AWAITING_DIAGNOSIS, saga.getValue().getState());
        assertEquals(BRANCH, saga.getValue().getBranchId());

        SagaEnvelope command = onlyCommand();
        assertEquals(SagaMessageType.START_DIAGNOSIS, command.type());
        assertEquals(WORK_ORDER, command.workOrderId());
        assertEquals(BRANCH, command.branchId());
        assertEquals(TRACE, command.correlationId());
    }

    @Test
    void onReply_forAWorkOrderWithoutASaga_isAnError() {
        when(instances.findByWorkOrderId(WORK_ORDER)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> orchestrator.onReply(reply(SagaMessageType.DIAGNOSIS_STARTED)));
    }

    @Test
    void onReply_whenTheInboxAlreadySawTheMessage_changesNothing() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_DIAGNOSIS);
        when(inbox.recordIfNew(any(), anyLong(), anyString())).thenReturn(false);

        orchestrator.onReply(reply(SagaMessageType.DIAGNOSIS_STARTED));

        assertEquals(SagaState.AWAITING_DIAGNOSIS, saga.getState());
        verify(workOrders, never()).markUnderDiagnosis(anyLong(), any());
        verify(outbox, never()).append(any());
    }

    @Test
    void diagnosisStarted_movesTheSagaAndTheWorkOrder() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_DIAGNOSIS);

        orchestrator.onReply(reply(SagaMessageType.DIAGNOSIS_STARTED));

        assertEquals(SagaState.DIAGNOSING, saga.getState());
        verify(workOrders).markUnderDiagnosis(WORK_ORDER, saga.getSagaId());
    }

    @Test
    void diagnosisCompleted_pricesLocallyAndAsksTheBudget() {
        SagaInstance saga = sagaAt(SagaState.DIAGNOSING);
        SagaReply completed = SagaReply.builder()
                .messageId(UUID.randomUUID())
                .type(SagaMessageType.DIAGNOSIS_COMPLETED)
                .workOrderId(WORK_ORDER)
                .correlationId(TRACE)
                .description("Vazamento")
                .items(ITEMS)
                .build();

        orchestrator.onReply(completed);

        assertEquals(SagaState.AWAITING_BUDGET, saga.getState());
        verify(workOrders).recordPricedDiagnosis(WORK_ORDER, saga.getSagaId(), "Vazamento", ITEMS);

        SagaEnvelope command = onlyCommand();
        assertEquals(SagaMessageType.CREATE_BUDGET, command.type());
        assertEquals(completed.messageId(), command.causationId());
        assertEquals(TRACE, command.correlationId());
    }

    @Test
    void budgetCreated_opensTheApprovalDeadlineAndShowsItToTheCustomer() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_BUDGET);
        SagaReply created = SagaReply.builder()
                .messageId(UUID.randomUUID())
                .type(SagaMessageType.BUDGET_CREATED)
                .workOrderId(WORK_ORDER)
                .budgetId("B-99")
                .build();

        orchestrator.onReply(created);

        assertEquals(SagaState.AWAITING_APPROVAL, saga.getState());
        assertEquals("B-99", saga.getBudgetId());
        assertEquals(APPROVAL_DEADLINE, saga.getDeadlineAt());
        verify(workOrders).markPendingApproval(WORK_ORDER, saga.getSagaId());
    }

    @Test
    void budgetApproved_reservesThePartsBeforeAskingForTheMoney() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_APPROVAL);

        orchestrator.onReply(reply(SagaMessageType.BUDGET_APPROVED));

        assertEquals(SagaState.AWAITING_PAYMENT_LINK, saga.getState());
        verify(parts).reserve(WORK_ORDER);
        verify(workOrders).markAwaitingPayment(WORK_ORDER, saga.getSagaId());
        assertEquals(SagaMessageType.REQUEST_PAYMENT, onlyCommand().type());
    }

    @Test
    void budgetApproved_withoutStock_compensatesInsteadOfRetrying() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_APPROVAL);
        doThrow(new StockException("Insufficient stock for part: Filtro de oleo"))
                .when(parts).reserve(WORK_ORDER);

        orchestrator.onReply(reply(SagaMessageType.BUDGET_APPROVED));

        assertEquals(SagaState.COMPENSATING, saga.getState());
        assertEquals(CancelReason.OUT_OF_STOCK, saga.getCancelReason());
        assertEquals(List.of(SagaMessageType.CANCEL_BUDGET, SagaMessageType.CANCEL_DIAGNOSIS),
                sentCommands().stream().map(SagaEnvelope::type).toList());
        verify(parts, never()).release(anyLong());
        verify(workOrders, never()).markAwaitingPayment(anyLong(), any());
    }

    @Test
    void paymentConfirmed_takesThePartsFromStockAndCommandsTheRepair() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_PAYMENT);
        SagaReply confirmed = SagaReply.builder()
                .messageId(UUID.randomUUID())
                .type(SagaMessageType.PAYMENT_CONFIRMED)
                .workOrderId(WORK_ORDER)
                .paymentId("MP-1234")
                .build();

        orchestrator.onReply(confirmed);

        assertEquals(SagaState.AWAITING_REPAIR, saga.getState());
        assertEquals("MP-1234", saga.getPaymentId());
        verify(parts).confirm(WORK_ORDER);
        verify(workOrders).markInProgress(WORK_ORDER, saga.getSagaId());
        assertEquals(SagaMessageType.START_REPAIR, onlyCommand().type());
    }

    @Test
    void repairCompleted_isThePivot() {
        SagaInstance saga = sagaAt(SagaState.REPAIRING);

        orchestrator.onReply(reply(SagaMessageType.REPAIR_COMPLETED));

        assertEquals(SagaState.COMPLETED, saga.getState());
        verify(workOrders).markCompleted(WORK_ORDER, saga.getSagaId());
    }

    @Test
    void paymentAttemptRejected_keepsWaitingForTheDeadline() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_PAYMENT);

        orchestrator.onReply(reply(SagaMessageType.PAYMENT_ATTEMPT_REJECTED));

        assertEquals(SagaState.AWAITING_PAYMENT, saga.getState());
        verify(outbox, never()).append(any());
    }

    @Test
    void anUnknownReplyType_touchesNothing() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_PAYMENT);

        orchestrator.onReply(reply("SomethingElse"));

        assertEquals(SagaState.AWAITING_PAYMENT, saga.getState());
        verify(outbox, never()).append(any());
    }

    @Test
    void rejectedBudget_undoesOnlyWhatHappened() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_APPROVAL);

        orchestrator.onReply(reply(SagaMessageType.BUDGET_REJECTED));

        assertEquals(CancelReason.REJECTED_BY_CUSTOMER, saga.getCancelReason());
        assertEquals(List.of(SagaMessageType.CANCEL_BUDGET, SagaMessageType.CANCEL_DIAGNOSIS),
                sentCommands().stream().map(SagaEnvelope::type).toList());
        verify(parts, never()).release(anyLong());
        verify(parts, never()).restore(anyLong());
    }

    @Test
    void repairFailed_refundsAndPutsThePartsBackBeforeCancelling() {
        SagaInstance saga = sagaAt(SagaState.REPAIRING);

        orchestrator.onReply(reply(SagaMessageType.REPAIR_FAILED));

        assertEquals(CancelReason.REPAIR_FAILED, saga.getCancelReason());
        assertEquals(List.of(SagaMessageType.REFUND_PAYMENT, SagaMessageType.CANCEL_BUDGET,
                        SagaMessageType.CANCEL_DIAGNOSIS),
                sentCommands().stream().map(SagaEnvelope::type).toList());
        verify(parts).restore(WORK_ORDER);
        verify(parts, never()).release(anyLong());
    }

    @Test
    void anExpiredPaymentReleasesTheReservationAndCancelsTheCharge() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_PAYMENT);
        saga.setDeadlineAt(Instant.parse("2026-10-21T12:00:00Z"));
        when(instances.claimExpired(any(Instant.class), anyInt())).thenReturn(List.of(saga));

        orchestrator.expireDeadlines(10);

        assertEquals(CancelReason.PAYMENT_EXPIRED, saga.getCancelReason());
        assertEquals(List.of(SagaMessageType.CANCEL_PAYMENT, SagaMessageType.CANCEL_BUDGET,
                        SagaMessageType.CANCEL_DIAGNOSIS),
                sentCommands().stream().map(SagaEnvelope::type).toList());
        verify(parts).release(WORK_ORDER);
    }

    @Test
    void anExpiredApprovalCancelsTheBudget() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_APPROVAL);
        when(instances.claimExpired(any(Instant.class), anyInt())).thenReturn(List.of(saga));

        orchestrator.expireDeadlines(10);

        assertEquals(CancelReason.APPROVAL_EXPIRED, saga.getCancelReason());
    }

    @Test
    void theWorkOrderIsOnlyCanceledAfterTheLastCompensationIsConfirmed() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_APPROVAL);
        orchestrator.onReply(reply(SagaMessageType.BUDGET_REJECTED));

        verify(workOrders, never()).markCanceled(anyLong(), any(), any());
        assertEquals(SagaState.COMPENSATING, saga.getState());

        orchestrator.onReply(reply(SagaMessageType.BUDGET_CANCELED));
        verify(workOrders, never()).markCanceled(anyLong(), any(), any());
        assertEquals(SagaState.COMPENSATING, saga.getState());

        orchestrator.onReply(reply(SagaMessageType.DIAGNOSIS_CANCELED));
        assertEquals(SagaState.COMPENSATED, saga.getState());
        verify(workOrders).markCanceled(WORK_ORDER, saga.getSagaId(), CancelReason.REJECTED_BY_CUSTOMER);
    }

    @Test
    void aConfirmationTheSagaWasNotWaitingFor_isIgnored() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_PAYMENT);

        orchestrator.onReply(reply(SagaMessageType.PAYMENT_REFUNDED));

        assertEquals(SagaState.AWAITING_PAYMENT, saga.getState());
        verify(workOrders, never()).markCanceled(anyLong(), any(), any());
    }

    @Test
    void aPaymentThatLandsAfterTheCompensationIsRefundedOnTheSpot() {
        SagaInstance saga = sagaAt(SagaState.COMPENSATING);
        SagaReply confirmed = SagaReply.builder()
                .messageId(UUID.randomUUID())
                .type(SagaMessageType.PAYMENT_CONFIRMED)
                .workOrderId(WORK_ORDER)
                .paymentId("MP-5555")
                .build();

        orchestrator.onReply(confirmed);

        assertEquals(SagaMessageType.REFUND_PAYMENT, onlyCommand().type());
        verify(parts, never()).confirm(anyLong());
        verify(workOrders, never()).markInProgress(anyLong(), any());
    }

    @Test
    void cancelByStaff_undoesWhatIsMissingFromTheCurrentState() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_PAYMENT);

        orchestrator.cancel(WORK_ORDER, CancelReason.CANCELED_BY_STAFF);

        assertEquals(SagaState.COMPENSATING, saga.getState());
        assertEquals(CancelReason.CANCELED_BY_STAFF, saga.getCancelReason());
        verify(parts).release(WORK_ORDER);
        assertEquals(List.of(SagaMessageType.CANCEL_PAYMENT, SagaMessageType.CANCEL_BUDGET,
                        SagaMessageType.CANCEL_DIAGNOSIS),
                sentCommands().stream().map(SagaEnvelope::type).toList());
    }

    @Test
    void cancelByStaff_beforeTheDiagnosisStarts_cancelsTheWorkOrderRightAway() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_DIAGNOSIS);

        orchestrator.cancel(WORK_ORDER, CancelReason.CANCELED_BY_STAFF);

        assertEquals(SagaState.COMPENSATED, saga.getState());
        verify(workOrders).markCanceled(WORK_ORDER, saga.getSagaId(), CancelReason.CANCELED_BY_STAFF);
        verify(outbox, never()).append(any());
    }

    @Test
    void cancelByStaff_afterThePivot_isRefused() {
        SagaInstance saga = sagaAt(SagaState.COMPLETED);

        assertThrows(ConflictException.class,
                () -> orchestrator.cancel(WORK_ORDER, CancelReason.CANCELED_BY_STAFF));

        assertEquals(SagaState.COMPLETED, saga.getState());
        assertNull(saga.getCancelReason());
        verify(outbox, never()).append(any());
    }

    @Test
    void cancelByStaff_onASagaAlreadyCompensating_doesNotCompensateTwice() {
        SagaInstance saga = sagaAt(SagaState.COMPENSATING);

        orchestrator.cancel(WORK_ORDER, CancelReason.CANCELED_BY_STAFF);

        verify(outbox, never()).append(any());
        assertTrue(saga.getState().isCompensating());
    }

    @Test
    void cancelByStaff_forAWorkOrderWithoutASaga_isAnError() {
        when(instances.findByWorkOrderId(WORK_ORDER)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> orchestrator.cancel(WORK_ORDER, CancelReason.CANCELED_BY_STAFF));
    }

    @Test
    void everyCommandCarriesTheSagaTheBranchAndTheWorkOrder() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_APPROVAL);

        orchestrator.onReply(reply(SagaMessageType.BUDGET_APPROVED));

        SagaEnvelope command = onlyCommand();
        assertEquals(saga.getSagaId(), command.sagaId());
        assertEquals(BRANCH, command.branchId());
        assertEquals(WORK_ORDER, command.workOrderId());
        assertEquals(SagaEnvelope.CURRENT_VERSION, command.version());
    }

    @Test
    void aLateDiagnosisStartedOnACompensatingSaga_isANoOp() {
        SagaInstance saga = sagaAt(SagaState.COMPENSATING);

        orchestrator.onReply(reply(SagaMessageType.DIAGNOSIS_STARTED));

        assertEquals(SagaState.COMPENSATING, saga.getState());
        verify(workOrders, never()).markUnderDiagnosis(anyLong(), any());
    }

    @Test
    void repairStartedAndPaymentRequested_onlyMoveTheSagaState() {
        SagaInstance awaitingLink = sagaAt(SagaState.AWAITING_PAYMENT_LINK);
        orchestrator.onReply(reply(SagaMessageType.PAYMENT_REQUESTED));
        assertEquals(SagaState.AWAITING_PAYMENT, awaitingLink.getState());
        assertEquals(PAYMENT_DEADLINE, awaitingLink.getDeadlineAt());

        SagaInstance awaitingRepair = sagaAt(SagaState.AWAITING_REPAIR);
        orchestrator.onReply(reply(SagaMessageType.REPAIR_STARTED));
        assertEquals(SagaState.REPAIRING, awaitingRepair.getState());

        verify(outbox, never()).append(any());
    }

    @Test
    void failuresBeforeAnyBudgetOnlyCancelTheDiagnosis() {
        SagaInstance diagnosing = sagaAt(SagaState.DIAGNOSING);
        orchestrator.onReply(reply(SagaMessageType.DIAGNOSIS_FAILED));

        assertEquals(CancelReason.DIAGNOSIS_FAILED, diagnosing.getCancelReason());
        assertEquals(List.of(SagaMessageType.CANCEL_DIAGNOSIS),
                sentCommands().stream().map(SagaEnvelope::type).toList());
    }

    @Test
    void aBudgetThatCouldNotBeCreatedCancelsTheDiagnosis() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_BUDGET);

        orchestrator.onReply(reply(SagaMessageType.BUDGET_CREATION_FAILED));

        assertEquals(CancelReason.BUDGET_CREATION_FAILED, saga.getCancelReason());
        assertEquals(List.of(SagaMessageType.CANCEL_DIAGNOSIS),
                sentCommands().stream().map(SagaEnvelope::type).toList());
    }

    @Test
    void aChargeThatCouldNotBeCreatedReleasesTheReservation() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_PAYMENT_LINK);

        orchestrator.onReply(reply(SagaMessageType.PAYMENT_REQUEST_FAILED));

        assertEquals(CancelReason.PAYMENT_REQUEST_FAILED, saga.getCancelReason());
        verify(parts).release(WORK_ORDER);
        assertEquals(List.of(SagaMessageType.CANCEL_BUDGET, SagaMessageType.CANCEL_DIAGNOSIS),
                sentCommands().stream().map(SagaEnvelope::type).toList());
    }

    @Test
    void theRefundCommandCarriesThePaymentThatWasConfirmedAndTheReason() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_REPAIR);
        saga.setPaymentId("MP-7777");

        orchestrator.onReply(reply(SagaMessageType.REPAIR_FAILED));

        SagaEnvelope refund = sentCommands().stream()
                .filter(command -> SagaMessageType.REFUND_PAYMENT.equals(command.type()))
                .findFirst().orElseThrow();
        RefundPaymentPayload payload = (RefundPaymentPayload) refund.payload();
        assertEquals("MP-7777", payload.paymentId());
        assertEquals(CancelReason.REPAIR_FAILED.name(), payload.reason());
        assertEquals(saga.getSagaId(), refund.sagaId());
    }
}
