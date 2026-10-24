package br.com.fiap.postech.carworkshop.saga.domain.message;

public record StartDiagnosisPayload(String vehiclePlate, String complaint) implements SagaPayload {
}
