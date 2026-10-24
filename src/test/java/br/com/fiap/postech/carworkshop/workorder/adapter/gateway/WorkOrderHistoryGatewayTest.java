package br.com.fiap.postech.carworkshop.workorder.adapter.gateway;

import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrderStatusChange;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.WorkOrderStatusHistoryJpaEntity;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.WorkOrderStatusHistoryPanacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderHistoryGatewayTest {

    private static final UUID SAGA_ID = UUID.fromString("6f1d0000-0000-4000-8000-000000000002");
    private static final Instant WHEN = Instant.parse("2026-10-22T12:00:00Z");

    private final WorkOrderStatusHistoryPanacheRepository repository =
            mock(WorkOrderStatusHistoryPanacheRepository.class);
    private WorkOrderHistoryGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new WorkOrderHistoryGateway();
        gateway.repository = repository;
    }

    @Test
    void record_writesEveryFieldOfTheTransition() {
        gateway.record(new WorkOrderStatusChange(42L, "SP-01", StatusWO.CANCELED,
                CancelReason.PAYMENT_EXPIRED, SAGA_ID, WHEN));

        ArgumentCaptor<WorkOrderStatusHistoryJpaEntity> captor =
                ArgumentCaptor.forClass(WorkOrderStatusHistoryJpaEntity.class);
        verify(repository).persist(captor.capture());
        WorkOrderStatusHistoryJpaEntity row = captor.getValue();
        assertEquals(42L, row.getWorkOrderId());
        assertEquals("SP-01", row.getBranchId());
        assertEquals(StatusWO.CANCELED, row.getStatus());
        assertEquals(CancelReason.PAYMENT_EXPIRED, row.getReason());
        assertEquals(SAGA_ID, row.getSagaId());
        assertEquals(WHEN, row.getChangedAt());
    }

    @Test
    void findByWorkOrderId_mapsTheRowsBackToTheDomain() {
        when(repository.findByWorkOrderId(42L)).thenReturn(List.of(
                WorkOrderStatusHistoryJpaEntity.builder().workOrderId(42L).branchId("SP-01")
                        .status(StatusWO.RECEIVED).changedAt(WHEN).build()));

        List<WorkOrderStatusChange> history = gateway.findByWorkOrderId(42L);

        assertEquals(1, history.size());
        assertEquals(StatusWO.RECEIVED, history.get(0).status());
        assertEquals(WHEN, history.get(0).changedAt());
    }
}
