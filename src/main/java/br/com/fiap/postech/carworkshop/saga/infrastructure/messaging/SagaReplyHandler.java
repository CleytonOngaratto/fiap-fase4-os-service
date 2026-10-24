package br.com.fiap.postech.carworkshop.saga.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.saga.domain.message.SagaItem;
import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaOrchestratorUseCase;
import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaReply;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class SagaReplyHandler {

    @Inject
    SagaOrchestratorUseCase orchestrator;

    // Bean separado do consumidor de proposito: autoinvocacao nao passa pelo interceptador, e o @Transactional
    // seria ignorado em silencio. Lancar aqui e o que impede o consumidor de apagar a mensagem.
    @Transactional
    public void apply(JsonNode envelope) {
        orchestrator.onReply(toReply(envelope));
    }

    private static SagaReply toReply(JsonNode envelope) {
        JsonNode payload = envelope.path(SagaEnvelopeCodec.FIELD_PAYLOAD);
        return SagaReply.builder()
                .messageId(uuid(envelope, SagaEnvelopeCodec.FIELD_MESSAGE_ID))
                .type(text(envelope, SagaEnvelopeCodec.FIELD_TYPE))
                .workOrderId(envelope.path(SagaEnvelopeCodec.FIELD_WORK_ORDER_ID).asLong())
                .correlationId(text(envelope, SagaEnvelopeCodec.FIELD_CORRELATION_ID))
                .description(text(payload, "description"))
                .items(items(payload.path("items")))
                .budgetId(text(payload, "budgetId"))
                .paymentId(text(payload, "paymentId"))
                .reason(text(payload, "reason"))
                .build();
    }

    // Tolerant reader: campo ausente volta nulo e campo novo e ignorado, que e o contrato de evolucao do
    // catalogo. O preco nao vem do participante: quem precifica e o catalogo do proprio OS.
    private static List<SagaItem> items(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<SagaItem> items = new ArrayList<>();
        for (JsonNode item : node) {
            items.add(new SagaItem(
                    text(item, "type"),
                    item.path("catalogId").asLong(),
                    text(item, "description"),
                    item.path("quantity").asInt(1),
                    BigDecimal.ZERO));
        }
        return items;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
