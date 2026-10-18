package br.com.fiap.postech.carworkshop.workorder.domain.message;

// Sem complaint nem branchId: a abertura ainda nao recebe relato nem filial, e o contrato omite o campo em vez
// de mandar valor inventado, que ficaria indistinguivel do real em log e painel.
public record StartDiagnosisPayload(String vehiclePlate) implements SagaPayload {
}
