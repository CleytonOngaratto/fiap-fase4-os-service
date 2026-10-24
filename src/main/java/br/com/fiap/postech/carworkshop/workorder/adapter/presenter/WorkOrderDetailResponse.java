package br.com.fiap.postech.carworkshop.workorder.adapter.presenter;

import br.com.fiap.postech.carworkshop.autoservice.adapter.presenter.AutoServiceResponse;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record WorkOrderDetailResponse(
        Long id,
        @JsonProperty("customer_id") Long customerId,
        @JsonProperty("vehicle_id") Long vehicleId,
        @JsonProperty("branch_id") String branchId,
        String complaint,
        StatusWO status,
        @JsonProperty("creation_date") LocalDateTime creationDate,
        @JsonProperty("end_date") LocalDateTime endDate,
        @JsonProperty("diagnostic_description") String diagnosticDescription,
        @JsonProperty("budget_value") BigDecimal budgetValue,
        @JsonProperty("cancel_reason") CancelReason cancelReason,
        List<AutoServiceResponse> services,
        List<WorkOrderPartResponse> parts
) {
    public static WorkOrderDetailResponse from(WorkOrder wo) {
        return new WorkOrderDetailResponse(
                wo.getId(),
                wo.getCustomerId(),
                wo.getVehicleId(),
                wo.getBranchId(),
                wo.getComplaint(),
                wo.getStatus(),
                wo.getCreationDate(),
                wo.getEndDate(),
                wo.getDiagnosticDescription(),
                wo.getBudgetValue(),
                wo.getCancelReason(),
                WorkOrderItems.services(wo),
                WorkOrderItems.parts(wo)
        );
    }
}
