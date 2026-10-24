package br.com.fiap.postech.carworkshop.saga.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.shared.domain.exception.DomainException;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaOutboxJpaEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class SagaEnvelopeCodec {

    static final String FIELD_MESSAGE_ID = "messageId";
    static final String FIELD_TYPE = "type";
    static final String FIELD_VERSION = "version";
    static final String FIELD_OCCURRED_AT = "occurredAt";
    static final String FIELD_SAGA_ID = "sagaId";
    static final String FIELD_WORK_ORDER_ID = "workOrderId";
    static final String FIELD_BRANCH_ID = "branchId";
    static final String FIELD_CORRELATION_ID = "correlationId";
    static final String FIELD_CAUSATION_ID = "causationId";
    static final String FIELD_PAYLOAD = "payload";

    @Inject
    ObjectMapper objectMapper;

    public String encode(SagaOutboxJpaEntity row) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put(FIELD_MESSAGE_ID, row.messageId.toString());
        envelope.put(FIELD_TYPE, row.getType());
        envelope.put(FIELD_VERSION, row.getVersion());
        envelope.put(FIELD_OCCURRED_AT, row.getOccurredAt().toString());
        envelope.put(FIELD_SAGA_ID, row.getSagaId().toString());
        envelope.put(FIELD_WORK_ORDER_ID, row.getWorkOrderId());
        putIfPresent(envelope, FIELD_BRANCH_ID, row.getBranchId());
        putIfPresent(envelope, FIELD_CORRELATION_ID, row.getCorrelationId());
        putIfPresent(envelope, FIELD_CAUSATION_ID, asText(row.getCausationId()));
        envelope.set(FIELD_PAYLOAD, readPayload(row.getPayload()));
        return envelope.toString();
    }

    public JsonNode decode(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new DomainException("Saga message body is not valid JSON.");
        }
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new DomainException("Outbox payload is not valid JSON.");
        }
    }

    private static void putIfPresent(ObjectNode envelope, String field, String value) {
        if (value != null && !value.isBlank()) {
            envelope.put(field, value);
        }
    }

    private static String asText(UUID value) {
        return value == null ? null : value.toString();
    }
}
