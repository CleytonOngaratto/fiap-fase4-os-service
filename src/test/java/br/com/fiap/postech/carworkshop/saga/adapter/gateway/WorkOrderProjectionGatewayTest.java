package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.autoservice.domain.entity.AutoService;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.saga.domain.message.SagaItem;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.WorkOrderProjectionPort.WorkOrderSnapshot;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.DiagnosedItem;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderTransition;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderProjectionGatewayTest {

    private static final Long WORK_ORDER = 42L;
    private static final UUID SAGA_ID = UUID.fromString("6f1d0000-0000-4000-8000-000000000002");

    private final WorkOrderUseCase workOrders = mock(WorkOrderUseCase.class);
    private WorkOrderProjectionGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new WorkOrderProjectionGateway();
        gateway.workOrders = workOrders;
        when(workOrders.applySagaTransition(any(), any(), any(), any())).thenReturn(true);
    }

    private WorkOrder priced() {
        WorkOrder workOrder = WorkOrder.create(1L, "a@b.com", "12345678901", 1L, "ABC-1234",
                "SP-01", "ruido");
        workOrder.setId(WORK_ORDER);
        workOrder.setStatus(StatusWO.UNDER_DIAGNOSIS);
        workOrder.setBudgetValue(new BigDecimal("220.00"));
        workOrder.setServices(List.of(AutoService.builder().id(1L).description("Troca de oleo")
                .price(new BigDecimal("150.00")).build()));
        workOrder.setParts(List.of(PartsAndSupply.builder().id(3L).description("Filtro")
                .price(new BigDecimal("35.00")).quantity(2).build()));
        return workOrder;
    }

    @Test
    void snapshotOf_turnsTheWorkOrderIntoTheItemsTheBudgetNeeds() {
        when(workOrders.findById(WORK_ORDER)).thenReturn(priced());

        WorkOrderSnapshot snapshot = gateway.snapshotOf(WORK_ORDER);

        assertEquals("12345678901", snapshot.customerDocument());
        assertEquals("SP-01", snapshot.branchId());
        assertEquals(new BigDecimal("220.00"), snapshot.total());
        assertFalse(snapshot.pastPivot());
        assertEquals(2, snapshot.items().size());
        assertEquals(SagaItem.SERVICE, snapshot.items().get(0).type());
        assertEquals(SagaItem.PART, snapshot.items().get(1).type());
        assertEquals(2, snapshot.items().get(1).quantity());
    }

    @Test
    void snapshotOf_withoutAPricedBudget_reportsZero() {
        WorkOrder workOrder = priced();
        workOrder.setBudgetValue(null);
        workOrder.setServices(null);
        workOrder.setParts(null);
        when(workOrders.findById(WORK_ORDER)).thenReturn(workOrder);

        WorkOrderSnapshot snapshot = gateway.snapshotOf(WORK_ORDER);

        assertEquals(0, snapshot.total().signum());
        assertTrue(snapshot.items().isEmpty());
    }

    @Test
    void snapshotOf_reportsThePivotOnceTheRepairIsComplete() {
        WorkOrder workOrder = priced();
        workOrder.setStatus(StatusWO.COMPLETED);
        when(workOrders.findById(WORK_ORDER)).thenReturn(workOrder);

        assertTrue(gateway.snapshotOf(WORK_ORDER).pastPivot());
    }

    @Test
    void eachIntentionMapsToItsTransition() {
        assertTrue(gateway.markUnderDiagnosis(WORK_ORDER, SAGA_ID));
        verify(workOrders).applySagaTransition(WORK_ORDER, WorkOrderTransition.START_DIAGNOSIS, SAGA_ID, null);

        assertTrue(gateway.markPendingApproval(WORK_ORDER, SAGA_ID));
        verify(workOrders).applySagaTransition(WORK_ORDER, WorkOrderTransition.PEND_APPROVAL, SAGA_ID, null);

        assertTrue(gateway.markAwaitingPayment(WORK_ORDER, SAGA_ID));
        verify(workOrders).applySagaTransition(WORK_ORDER, WorkOrderTransition.AWAIT_PAYMENT, SAGA_ID, null);

        assertTrue(gateway.markInProgress(WORK_ORDER, SAGA_ID));
        verify(workOrders).applySagaTransition(WORK_ORDER, WorkOrderTransition.START_REPAIR, SAGA_ID, null);

        assertTrue(gateway.markCompleted(WORK_ORDER, SAGA_ID));
        verify(workOrders).applySagaTransition(WORK_ORDER, WorkOrderTransition.COMPLETE, SAGA_ID, null);

        assertTrue(gateway.markCanceled(WORK_ORDER, SAGA_ID, CancelReason.OUT_OF_STOCK));
        verify(workOrders).applySagaTransition(WORK_ORDER, WorkOrderTransition.CANCEL, SAGA_ID,
                CancelReason.OUT_OF_STOCK);
    }

    @Test
    void recordPricedDiagnosis_handsTheDiagnosedItemsToTheCatalogOwner() {
        when(workOrders.recordDiagnosis(eq(WORK_ORDER), eq("Vazamento"), any(), eq(SAGA_ID)))
                .thenReturn(true);

        assertTrue(gateway.recordPricedDiagnosis(WORK_ORDER, SAGA_ID, "Vazamento", List.of(
                new SagaItem(SagaItem.SERVICE, 1L, "Troca de oleo", 1, BigDecimal.ZERO),
                new SagaItem(SagaItem.PART, 3L, "Filtro", 2, BigDecimal.ZERO))));

        verify(workOrders).recordDiagnosis(WORK_ORDER, "Vazamento", List.of(
                new DiagnosedItem(false, 1L, 1),
                new DiagnosedItem(true, 3L, 2)), SAGA_ID);
    }

    @Test
    void recordPricedDiagnosis_withoutItems_stillReachesTheUseCase() {
        gateway.recordPricedDiagnosis(WORK_ORDER, SAGA_ID, "Sem itens", null);

        verify(workOrders).recordDiagnosis(WORK_ORDER, "Sem itens", List.of(), SAGA_ID);
    }
}
