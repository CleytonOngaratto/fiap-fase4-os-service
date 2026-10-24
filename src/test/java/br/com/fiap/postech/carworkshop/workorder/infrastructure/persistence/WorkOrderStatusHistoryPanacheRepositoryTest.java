package br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence;

import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class WorkOrderStatusHistoryPanacheRepositoryTest {

    private static final Long WORK_ORDER = 910L;

    @Inject
    WorkOrderStatusHistoryPanacheRepository repository;

    @Test
    @Transactional
    void returnsTheHistoryOfOneWorkOrderInChronologicalOrder() {
        UUID sagaId = UUID.randomUUID();
        Instant base = Instant.parse("2026-01-01T10:00:00Z");

        persist(WORK_ORDER, StatusWO.CANCELED, CancelReason.OUT_OF_STOCK, sagaId, base.plusSeconds(60));
        persist(WORK_ORDER, StatusWO.RECEIVED, null, sagaId, base);
        persist(WORK_ORDER + 1, StatusWO.RECEIVED, null, UUID.randomUUID(), base);

        List<WorkOrderStatusHistoryJpaEntity> history = repository.findByWorkOrderId(WORK_ORDER);

        assertEquals(2, history.size());
        assertEquals(StatusWO.RECEIVED, history.get(0).getStatus());
        assertEquals(StatusWO.CANCELED, history.get(1).getStatus());
        assertEquals(CancelReason.OUT_OF_STOCK, history.get(1).getReason());
        assertEquals(sagaId, history.get(0).getSagaId());
        assertEquals("SP-01", history.get(0).getBranchId());
        assertEquals(WORK_ORDER, history.get(1).getWorkOrderId());
    }

    private void persist(Long workOrderId, StatusWO status, CancelReason reason, UUID sagaId, Instant changedAt) {
        repository.persist(WorkOrderStatusHistoryJpaEntity.builder()
                .workOrderId(workOrderId)
                .branchId("SP-01")
                .status(status)
                .reason(reason)
                .sagaId(sagaId)
                .changedAt(changedAt)
                .build());
    }
}
