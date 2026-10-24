package br.com.fiap.postech.carworkshop.workorder.adapter.presenter;

import br.com.fiap.postech.carworkshop.autoservice.adapter.presenter.AutoServiceResponse;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrderStatusChange;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record WorkOrderTrackingResponse(
        Long id,
        StatusWO status,
        @JsonProperty("branch_id") String branchId,
        @JsonProperty("vehicle_plate") String vehiclePlate,
        String complaint,
        @JsonProperty("diagnostic_description") String diagnosticDescription,
        @JsonProperty("budget_value") BigDecimal budgetValue,
        @JsonProperty("cancel_reason") CancelReason cancelReason,
        List<AutoServiceResponse> services,
        List<WorkOrderPartResponse> parts,
        List<WorkOrderStatusChangeResponse> history
) {
    public static WorkOrderTrackingResponse from(WorkOrder wo, List<WorkOrderStatusChange> history) {
        return new WorkOrderTrackingResponse(
                wo.getId(),
                wo.getStatus(),
                wo.getBranchId(),
                wo.getVehiclePlate(),
                wo.getComplaint(),
                wo.getDiagnosticDescription(),
                wo.getBudgetValue(),
                wo.getCancelReason(),
                WorkOrderItems.services(wo),
                WorkOrderItems.parts(wo),
                history == null
                        ? List.of()
                        : history.stream().map(WorkOrderStatusChangeResponse::from).toList()
        );
    }
}
