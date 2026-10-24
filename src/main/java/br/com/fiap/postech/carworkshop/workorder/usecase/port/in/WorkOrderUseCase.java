package br.com.fiap.postech.carworkshop.workorder.usecase.port.in;

import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrderStatusChange;

import java.util.List;
import java.util.UUID;

public interface WorkOrderUseCase {
    WorkOrder create(OpenWorkOrderCommand command);
    List<WorkOrder> findAll();
    List<WorkOrder> findAllActive();
    WorkOrder findById(Long id);
    WorkOrder deliverWorkOrder(Long id);
    void deleteWorkOrder(Long id);
    WorkOrder findForCustomer(Long id);
    WorkOrder findForOwner(Long id, String requesterDocument);
    List<WorkOrderStatusChange> historyOf(Long id);
    Double getAverageCompletionTimeInHours();

    boolean applySagaTransition(Long id, WorkOrderTransition transition, UUID sagaId, CancelReason reason);
    boolean recordDiagnosis(Long id, String description, List<DiagnosedItem> items, UUID sagaId);
}
