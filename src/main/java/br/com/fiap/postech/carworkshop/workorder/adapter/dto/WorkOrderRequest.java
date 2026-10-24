package br.com.fiap.postech.carworkshop.workorder.adapter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WorkOrderRequest {

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("vehicle_id")
    private Long vehicleId;

    @JsonProperty("branch_id")
    private String branchId;

    private String complaint;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }
    public String getComplaint() { return complaint; }
    public void setComplaint(String complaint) { this.complaint = complaint; }
}
