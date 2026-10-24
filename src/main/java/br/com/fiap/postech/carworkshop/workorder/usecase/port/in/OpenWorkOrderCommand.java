package br.com.fiap.postech.carworkshop.workorder.usecase.port.in;

public record OpenWorkOrderCommand(Long customerId, Long vehicleId, String branchId, String complaint) {
}
