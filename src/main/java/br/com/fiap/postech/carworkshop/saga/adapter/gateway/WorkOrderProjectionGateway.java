package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.autoservice.domain.entity.AutoService;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.saga.domain.message.SagaItem;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.WorkOrderProjectionPort;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.DiagnosedItem;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderTransition;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class WorkOrderProjectionGateway implements WorkOrderProjectionPort {

    @Inject
    WorkOrderUseCase workOrders;

    @Override
    public WorkOrderSnapshot snapshotOf(Long workOrderId) {
        WorkOrder workOrder = workOrders.findById(workOrderId);
        return new WorkOrderSnapshot(workOrder.getId(), workOrder.getBranchId(),
                workOrder.getCustomerDocument(), itemsOf(workOrder),
                workOrder.getBudgetValue() == null ? BigDecimal.ZERO : workOrder.getBudgetValue(),
                workOrder.isPastPivot());
    }

    @Override
    public boolean markUnderDiagnosis(Long workOrderId, UUID sagaId) {
        return workOrders.applySagaTransition(workOrderId, WorkOrderTransition.START_DIAGNOSIS, sagaId, null);
    }

    @Override
    public boolean recordPricedDiagnosis(Long workOrderId, UUID sagaId, String description,
                                         List<SagaItem> items) {
        return workOrders.recordDiagnosis(workOrderId, description, diagnosed(items), sagaId);
    }

    @Override
    public boolean markPendingApproval(Long workOrderId, UUID sagaId) {
        return workOrders.applySagaTransition(workOrderId, WorkOrderTransition.PEND_APPROVAL, sagaId, null);
    }

    @Override
    public boolean markAwaitingPayment(Long workOrderId, UUID sagaId) {
        return workOrders.applySagaTransition(workOrderId, WorkOrderTransition.AWAIT_PAYMENT, sagaId, null);
    }

    @Override
    public boolean markInProgress(Long workOrderId, UUID sagaId) {
        return workOrders.applySagaTransition(workOrderId, WorkOrderTransition.START_REPAIR, sagaId, null);
    }

    @Override
    public boolean markCompleted(Long workOrderId, UUID sagaId) {
        return workOrders.applySagaTransition(workOrderId, WorkOrderTransition.COMPLETE, sagaId, null);
    }

    @Override
    public boolean markCanceled(Long workOrderId, UUID sagaId, CancelReason reason) {
        return workOrders.applySagaTransition(workOrderId, WorkOrderTransition.CANCEL, sagaId, reason);
    }

    private static List<DiagnosedItem> diagnosed(List<SagaItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> new DiagnosedItem(item.isPart(), item.catalogId(), item.quantity()))
                .toList();
    }

    private static List<SagaItem> itemsOf(WorkOrder workOrder) {
        List<SagaItem> items = new ArrayList<>();
        if (workOrder.getServices() != null) {
            for (AutoService service : workOrder.getServices()) {
                items.add(new SagaItem(SagaItem.SERVICE, service.getId(), service.getDescription(),
                        1, service.getPrice()));
            }
        }
        if (workOrder.getParts() != null) {
            for (PartsAndSupply part : workOrder.getParts()) {
                items.add(new SagaItem(SagaItem.PART, part.getId(), part.getDescription(),
                        part.getQuantity() == null ? 1 : part.getQuantity(), part.getPrice()));
            }
        }
        return items;
    }
}
