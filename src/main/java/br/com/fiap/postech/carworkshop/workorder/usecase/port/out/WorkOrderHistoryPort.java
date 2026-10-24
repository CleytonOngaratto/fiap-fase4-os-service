package br.com.fiap.postech.carworkshop.workorder.usecase.port.out;

import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrderStatusChange;

import java.util.List;

public interface WorkOrderHistoryPort {

    void record(WorkOrderStatusChange change);

    List<WorkOrderStatusChange> findByWorkOrderId(Long workOrderId);
}
