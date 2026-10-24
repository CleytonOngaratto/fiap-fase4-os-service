package br.com.fiap.postech.carworkshop.workorder.adapter.presenter;

import br.com.fiap.postech.carworkshop.autoservice.adapter.presenter.AutoServiceResponse;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record WorkOrderResponse(
        Long id,
        @JsonProperty("customer_id") Long customerId,
        @JsonProperty("vehicle_id") Long vehicleId,
        @JsonProperty("branch_id") String branchId,
        StatusWO status,
        @JsonProperty("creation_date") LocalDateTime creationDate,
        @JsonProperty("end_date") LocalDateTime endDate,
        List<AutoServiceResponse> services,
        List<WorkOrderPartResponse> parts
) {
    public static WorkOrderResponse from(WorkOrder wo) {
        return new WorkOrderResponse(
                wo.getId(),
                wo.getCustomerId(),
                wo.getVehicleId(),
                wo.getBranchId(),
                wo.getStatus(),
                wo.getCreationDate(),
                wo.getEndDate(),
                WorkOrderItems.services(wo),
                WorkOrderItems.parts(wo)
        );
    }
}
