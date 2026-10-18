package br.com.fiap.postech.carworkshop.workorder.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.workorder.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class SagaReplyHandler {

    @Inject
    WorkOrderUseCase workOrderUseCase;

    // Bean separado do consumidor de proposito: autoinvocacao nao passa pelo interceptador, e o @Transactional
    // seria ignorado em silencio. Lancar aqui e o que impede o consumidor de apagar a mensagem.
    @Transactional
    public void apply(JsonNode envelope) {
        String type = envelope.path(SagaEnvelopeCodec.FIELD_TYPE).asText(null);
        if (!SagaMessageType.DIAGNOSIS_STARTED.equals(type)) {
            log.warn("Unknown saga reply type {}: ignored.", type);
            return;
        }
        workOrderUseCase.startDiagnosis(envelope.path(SagaEnvelopeCodec.FIELD_WORK_ORDER_ID).asLong());
    }
}
