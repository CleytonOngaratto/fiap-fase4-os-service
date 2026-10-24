package br.com.fiap.postech.carworkshop.saga.usecase.port.out;

import br.com.fiap.postech.carworkshop.saga.domain.message.SagaItem;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface WorkOrderProjectionPort {

    WorkOrderSnapshot snapshotOf(Long workOrderId);

    boolean markUnderDiagnosis(Long workOrderId, UUID sagaId);

    boolean recordPricedDiagnosis(Long workOrderId, UUID sagaId, String description, List<SagaItem> items);

    boolean markPendingApproval(Long workOrderId, UUID sagaId);

    boolean markAwaitingPayment(Long workOrderId, UUID sagaId);

    boolean markInProgress(Long workOrderId, UUID sagaId);

    boolean markCompleted(Long workOrderId, UUID sagaId);

    boolean markCanceled(Long workOrderId, UUID sagaId, CancelReason reason);

    record WorkOrderSnapshot(Long workOrderId, String branchId, String customerDocument,
                             List<SagaItem> items, BigDecimal total, boolean pastPivot) {
    }
}
