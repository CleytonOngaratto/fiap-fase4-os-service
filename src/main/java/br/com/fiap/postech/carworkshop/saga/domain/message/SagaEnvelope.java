package br.com.fiap.postech.carworkshop.saga.domain.message;

import java.time.Instant;
import java.util.UUID;

public record SagaEnvelope(UUID messageId,
                           String type,
                           int version,
                           Instant occurredAt,
                           UUID sagaId,
                           Long workOrderId,
                           String branchId,
                           String correlationId,
                           UUID causationId,
                           SagaPayload payload) {

    public static final int CURRENT_VERSION = 1;

    public static SagaEnvelope command(String type, Long workOrderId, UUID sagaId, String branchId,
                                       String correlationId, UUID causationId, SagaPayload payload) {
        return new SagaEnvelope(UUID.randomUUID(),
                type,
                CURRENT_VERSION,
                Instant.now(),
                sagaId,
                workOrderId,
                branchId,
                correlationId,
                causationId,
                payload);
    }

    public static SagaEnvelope startDiagnosis(Long workOrderId, UUID sagaId, String branchId,
                                              String correlationId, SagaPayload payload) {
        return command(SagaMessageType.START_DIAGNOSIS, workOrderId, sagaId, branchId,
                correlationId, null, payload);
    }
}
