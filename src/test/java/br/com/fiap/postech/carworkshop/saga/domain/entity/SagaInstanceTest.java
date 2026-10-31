package br.com.fiap.postech.carworkshop.saga.domain.entity;

import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SagaInstanceTest {

    private static SagaInstance sagaAt(SagaState state) {
        SagaInstance saga = SagaInstance.start(42L, "SP-01");
        saga.setState(state);
        return saga;
    }

    @Test
    void start_beginsAwaitingTheDiagnosisWithAnIdentityOfItsOwn() {
        SagaInstance saga = SagaInstance.start(42L, "SP-01");

        assertNotNull(saga.getSagaId());
        assertEquals(42L, saga.getWorkOrderId());
        assertEquals("SP-01", saga.getBranchId());
        assertEquals(SagaState.AWAITING_DIAGNOSIS, saga.getState());
        assertNull(saga.getDeadlineAt());
        assertTrue(saga.getPendingCompensations().isEmpty());
        assertNotNull(saga.getCreatedAt());
    }

    @Test
    void advanceTo_movesForwardAndClearsThePreviousDeadline() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_APPROVAL);
        saga.setDeadlineAt(Instant.now());

        assertTrue(saga.advanceTo(SagaState.AWAITING_PAYMENT_LINK));
        assertEquals(SagaState.AWAITING_PAYMENT_LINK, saga.getState());
        assertNull(saga.getDeadlineAt());
    }

    @Test
    void advanceTo_withADeadlineKeepsIt() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_BUDGET);
        Instant deadline = Instant.parse("2026-10-24T12:00:00Z");

        assertTrue(saga.advanceTo(SagaState.AWAITING_APPROVAL, deadline));
        assertEquals(deadline, saga.getDeadlineAt());
    }

    @Test
    void advanceTo_theSameStateTwice_isANoOp() {
        SagaInstance saga = sagaAt(SagaState.DIAGNOSING);

        assertFalse(saga.advanceTo(SagaState.DIAGNOSING));
    }

    @Test
    void advanceTo_isRefusedOnceTheSagaIsCompensatingOrFinished() {
        for (SagaState state : List.of(SagaState.COMPENSATING, SagaState.COMPENSATED, SagaState.COMPLETED)) {
            SagaInstance saga = sagaAt(state);

            assertFalse(saga.advanceTo(SagaState.REPAIRING), "advance from " + state);
            assertEquals(state, saga.getState());
        }
    }

    @Test
    void hasExpired_onlyWhenThereIsADeadlineInThePastAndTheSagaIsStillRunning() {
        Instant now = Instant.parse("2026-10-22T12:00:00Z");

        SagaInstance running = sagaAt(SagaState.AWAITING_PAYMENT);
        running.setDeadlineAt(now.minusSeconds(1));
        assertTrue(running.hasExpired(now));

        SagaInstance future = sagaAt(SagaState.AWAITING_PAYMENT);
        future.setDeadlineAt(now.plusSeconds(60));
        assertFalse(future.hasExpired(now));

        SagaInstance noDeadline = sagaAt(SagaState.AWAITING_PAYMENT);
        assertFalse(noDeadline.hasExpired(now));

        SagaInstance compensating = sagaAt(SagaState.COMPENSATING);
        compensating.setDeadlineAt(now.minusSeconds(1));
        assertFalse(compensating.hasExpired(now));
    }

    @Test
    void beginCompensation_returnsTheStepsOfTheCurrentStateInOrder() {
        assertEquals(List.of(CompensationStep.CANCEL_PAYMENT, CompensationStep.RELEASE_PARTS,
                        CompensationStep.CANCEL_BUDGET, CompensationStep.CANCEL_DIAGNOSIS),
                sagaAt(SagaState.AWAITING_PAYMENT).beginCompensation(CancelReason.PAYMENT_EXPIRED));

        assertEquals(List.of(CompensationStep.REFUND_PAYMENT, CompensationStep.RESTORE_PARTS,
                        CompensationStep.CANCEL_BUDGET, CompensationStep.CANCEL_DIAGNOSIS),
                sagaAt(SagaState.REPAIRING).beginCompensation(CancelReason.REPAIR_FAILED));

        assertEquals(List.of(CompensationStep.CANCEL_DIAGNOSIS),
                sagaAt(SagaState.DIAGNOSING).beginCompensation(CancelReason.DIAGNOSIS_FAILED));

        assertEquals(List.of(CompensationStep.CANCEL_DIAGNOSIS), sagaAt(SagaState.AWAITING_DIAGNOSIS)
                .beginCompensation(CancelReason.CANCELED_BY_STAFF));
    }

    @Test
    void beginCompensation_onlyAwaitsTheStepsThatDependOnAParticipant() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_PAYMENT);

        saga.beginCompensation(CancelReason.PAYMENT_EXPIRED);

        assertEquals(SagaState.COMPENSATING, saga.getState());
        assertEquals(CancelReason.PAYMENT_EXPIRED, saga.getCancelReason());
        assertEquals(List.of(CompensationStep.CANCEL_PAYMENT, CompensationStep.CANCEL_BUDGET,
                        CompensationStep.CANCEL_DIAGNOSIS),
                List.copyOf(saga.getPendingCompensations()));
    }

    @Test
    void beginCompensation_twice_doesNotStartOver() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_APPROVAL);
        saga.beginCompensation(CancelReason.REJECTED_BY_CUSTOMER);

        assertEquals(List.of(), saga.beginCompensation(CancelReason.CANCELED_BY_STAFF));
        assertEquals(CancelReason.REJECTED_BY_CUSTOMER, saga.getCancelReason());
    }

    @Test
    void beginCompensation_afterThePivot_isRefused() {
        SagaInstance saga = sagaAt(SagaState.COMPLETED);

        assertThrows(ConflictException.class,
                () -> saga.beginCompensation(CancelReason.CANCELED_BY_STAFF));
    }

    @Test
    void theSagaIsOnlyCompensatedAfterTheLastConfirmation() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_APPROVAL);
        saga.beginCompensation(CancelReason.REJECTED_BY_CUSTOMER);

        assertTrue(saga.confirmCompensation(CompensationStep.CANCEL_BUDGET));
        assertFalse(saga.isCompensationComplete());
        assertFalse(saga.finishCompensation());

        assertTrue(saga.confirmCompensation(CompensationStep.CANCEL_DIAGNOSIS));
        assertTrue(saga.isCompensationComplete());
        assertTrue(saga.finishCompensation());
        assertEquals(SagaState.COMPENSATED, saga.getState());
    }

    @Test
    void confirmCompensation_forAStepItWasNotWaitingFor_isIgnored() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_APPROVAL);
        saga.beginCompensation(CancelReason.REJECTED_BY_CUSTOMER);

        assertFalse(saga.confirmCompensation(CompensationStep.REFUND_PAYMENT));
    }

    @Test
    void finishCompensation_onASagaThatIsNotCompensating_isANoOp() {
        assertFalse(sagaAt(SagaState.AWAITING_PAYMENT).finishCompensation());
    }

    @Test
    void rememberBudgetAndPayment_keepTheParticipantIdentifiers() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_BUDGET);

        saga.rememberBudget("B-77");
        saga.rememberPayment("MP-9988");

        assertEquals("B-77", saga.getBudgetId());
        assertEquals("MP-9988", saga.getPaymentId());
    }

    @Test
    void stateFlags_describeThePivotAndTheTerminalStates() {
        assertTrue(SagaState.COMPLETED.isPastPivot());
        assertTrue(SagaState.COMPLETED.isTerminal());
        assertTrue(SagaState.COMPENSATED.isTerminal());
        assertTrue(SagaState.COMPENSATING.isCompensating());
        assertFalse(SagaState.AWAITING_PAYMENT.isTerminal());
        assertFalse(SagaState.AWAITING_PAYMENT.isPastPivot());
    }

    @Test
    void isWaitingFor_comparesTheCurrentState() {
        SagaInstance saga = sagaAt(SagaState.AWAITING_PAYMENT);

        assertTrue(saga.isWaitingFor(SagaState.AWAITING_PAYMENT));
        assertFalse(saga.isWaitingFor(SagaState.AWAITING_REPAIR));
    }
}
