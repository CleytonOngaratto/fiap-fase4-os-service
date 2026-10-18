package br.com.fiap.postech.carworkshop.workorder.domain.message;

import java.time.Instant;
import java.util.UUID;

public record SagaEnvelope(UUID messageId,
                           String type,
                           int version,
                           Instant occurredAt,
                           UUID sagaId,
                           Long workOrderId,
                           String correlationId,
                           UUID causationId,
                           SagaPayload payload) {

    public static final int CURRENT_VERSION = 1;

    public static SagaEnvelope startDiagnosis(Long workOrderId, UUID sagaId,
                                              String correlationId, SagaPayload payload) {
        return new SagaEnvelope(UUID.randomUUID(),
                SagaMessageType.START_DIAGNOSIS,
                CURRENT_VERSION,
                Instant.now(),
                sagaId,
                workOrderId,
                correlationId,
                null,
                payload);
    }
}
