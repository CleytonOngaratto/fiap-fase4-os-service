package br.com.fiap.postech.carworkshop.workorder.adapter.presenter;

import br.com.fiap.postech.carworkshop.autoservice.adapter.presenter.AutoServiceResponse;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;

import java.util.List;

final class WorkOrderItems {

    private WorkOrderItems() {
    }

    static List<AutoServiceResponse> services(WorkOrder workOrder) {
        return workOrder.getServices() == null
                ? List.of()
                : workOrder.getServices().stream().map(AutoServiceResponse::from).toList();
    }

    static List<WorkOrderPartResponse> parts(WorkOrder workOrder) {
        return workOrder.getParts() == null
                ? List.of()
                : workOrder.getParts().stream().map(WorkOrderPartResponse::from).toList();
    }
}
