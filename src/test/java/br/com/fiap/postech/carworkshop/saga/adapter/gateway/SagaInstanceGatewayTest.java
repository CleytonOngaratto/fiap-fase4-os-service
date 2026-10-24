package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.saga.domain.entity.CompensationStep;
import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaInstance;
import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaState;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaInstanceJpaEntity;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaInstancePanacheRepository;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaInstanceGatewayTest {

    private static final UUID SAGA_ID = UUID.fromString("6f1d0000-0000-4000-8000-000000000002");

    private final SagaInstancePanacheRepository repository = mock(SagaInstancePanacheRepository.class);
    private SagaInstanceGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new SagaInstanceGateway();
        gateway.repository = repository;
    }

    private static SagaInstance compensating() {
        SagaInstance saga = SagaInstance.start(42L, "SP-01");
        saga.setSagaId(SAGA_ID);
        saga.setState(SagaState.COMPENSATING);
        saga.setCancelReason(CancelReason.PAYMENT_EXPIRED);
        saga.setBudgetId("B-77");
        saga.setPaymentId("MP-9988");
        saga.setPendingCompensations(new LinkedHashSet<>(
                List.of(CompensationStep.CANCEL_PAYMENT, CompensationStep.CANCEL_BUDGET)));
        return saga;
    }

    @Test
    void save_persistsANewSagaWithThePendingStepsEncoded() {
        when(repository.findById(SAGA_ID)).thenReturn(null);

        gateway.save(compensating());

        ArgumentCaptor<SagaInstanceJpaEntity> captor = ArgumentCaptor.forClass(SagaInstanceJpaEntity.class);
        verify(repository).persist(captor.capture());
        SagaInstanceJpaEntity row = captor.getValue();
        assertEquals(42L, row.getWorkOrderId());
        assertEquals("SP-01", row.getBranchId());
        assertEquals(SagaState.COMPENSATING, row.getState());
        assertEquals(CancelReason.PAYMENT_EXPIRED, row.getCancelReason());
        assertEquals("B-77", row.getBudgetId());
        assertEquals("CANCEL_PAYMENT,CANCEL_BUDGET", row.getPendingCompensations());
    }

    @Test
    void save_withoutPendingSteps_leavesTheColumnNull() {
        SagaInstance saga = SagaInstance.start(42L, "SP-01");
        when(repository.findById(saga.getSagaId())).thenReturn(null);

        gateway.save(saga);

        ArgumentCaptor<SagaInstanceJpaEntity> captor = ArgumentCaptor.forClass(SagaInstanceJpaEntity.class);
        verify(repository).persist(captor.capture());
        assertNull(captor.getValue().getPendingCompensations());
    }

    @Test
    void save_onAnExistingRow_updatesItInPlaceInsteadOfInserting() {
        SagaInstanceJpaEntity existing = SagaInstanceJpaEntity.builder()
                .sagaId(SAGA_ID).workOrderId(42L).state(SagaState.AWAITING_PAYMENT).version(3L).build();
        when(repository.findById(SAGA_ID)).thenReturn(existing);

        SagaInstance saved = gateway.save(compensating());

        verify(repository, never()).persist(any(SagaInstanceJpaEntity.class));
        assertEquals(SagaState.COMPENSATING, existing.getState());
        assertEquals("CANCEL_PAYMENT,CANCEL_BUDGET", existing.getPendingCompensations());
        assertEquals(3L, saved.getVersion());
    }

    @Test
    void findByWorkOrderId_decodesThePendingStepsBackIntoTheDomain() {
        when(repository.findByWorkOrderId(42L)).thenReturn(SagaInstanceJpaEntity.builder()
                .sagaId(SAGA_ID)
                .workOrderId(42L)
                .branchId("SP-01")
                .state(SagaState.COMPENSATING)
                .cancelReason(CancelReason.REPAIR_FAILED)
                .budgetId("B-1")
                .paymentId("MP-1")
                .pendingCompensations("REFUND_PAYMENT,CANCEL_DIAGNOSIS")
                .deadlineAt(Instant.parse("2026-10-24T12:00:00Z"))
                .version(7L)
                .build());

        SagaInstance saga = gateway.findByWorkOrderId(42L).orElseThrow();

        assertEquals(SAGA_ID, saga.getSagaId());
        assertEquals(SagaState.COMPENSATING, saga.getState());
        assertEquals(CancelReason.REPAIR_FAILED, saga.getCancelReason());
        assertEquals(List.of(CompensationStep.REFUND_PAYMENT, CompensationStep.CANCEL_DIAGNOSIS),
                List.copyOf(saga.getPendingCompensations()));
        assertEquals(7L, saga.getVersion());
    }

    @Test
    void findByWorkOrderId_withoutASaga_isEmpty() {
        when(repository.findByWorkOrderId(99L)).thenReturn(null);

        assertEquals(Optional.empty(), gateway.findByWorkOrderId(99L));
    }

    @Test
    void findByWorkOrderId_withAnEmptyPendingColumn_givesAnEmptySet() {
        when(repository.findByWorkOrderId(42L)).thenReturn(SagaInstanceJpaEntity.builder()
                .sagaId(SAGA_ID).workOrderId(42L).state(SagaState.AWAITING_PAYMENT)
                .pendingCompensations("  ").build());

        assertTrue(gateway.findByWorkOrderId(42L).orElseThrow().getPendingCompensations().isEmpty());
    }

    @Test
    void claimExpired_mapsEveryClaimedRow() {
        Instant now = Instant.parse("2026-10-22T12:00:00Z");
        when(repository.claimExpired(any(Instant.class), anyInt())).thenReturn(List.of(
                SagaInstanceJpaEntity.builder().sagaId(SAGA_ID).workOrderId(42L)
                        .state(SagaState.AWAITING_PAYMENT).build()));

        assertEquals(1, gateway.claimExpired(now, 10).size());
        verify(repository).claimExpired(now, 10);
    }
}
