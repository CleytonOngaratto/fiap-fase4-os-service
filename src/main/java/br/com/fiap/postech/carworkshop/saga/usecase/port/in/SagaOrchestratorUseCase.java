package br.com.fiap.postech.carworkshop.saga.usecase.port.in;

import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;

public interface SagaOrchestratorUseCase {

    void start(Long workOrderId, String branchId, String vehiclePlate, String complaint,
               String correlationId);

    void onReply(SagaReply reply);

    void cancel(Long workOrderId, CancelReason reason);

    void expireDeadlines(int batchSize);
}
