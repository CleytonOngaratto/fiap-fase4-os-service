package br.com.fiap.postech.carworkshop.saga.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.shared.domain.exception.DomainException;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaOutboxJpaEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SagaEnvelopeCodecTest {

    private static final UUID MESSAGE_ID = UUID.fromString("0b8f1c2e-0000-4000-8000-000000000001");
    private static final UUID SAGA_ID = UUID.fromString("6f1d0000-0000-4000-8000-000000000002");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-16T12:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SagaEnvelopeCodec codec;

    @BeforeEach
    void setUp() {
        codec = new SagaEnvelopeCodec();
        codec.objectMapper = objectMapper;
    }

    private static SagaOutboxJpaEntity.SagaOutboxJpaEntityBuilder row() {
        return SagaOutboxJpaEntity.builder()
                .messageId(MESSAGE_ID)
                .workOrderId(42L)
                .sagaId(SAGA_ID)
                .type("StartDiagnosis")
                .version(1)
                .occurredAt(OCCURRED_AT)
                .correlationId("EVIDENCIA-B3-001")
                .payload("{\"vehiclePlate\":\"ABC1D23\"}");
    }

    @Test
    void encode_writesTheCatalogEnvelopeWithThePayloadAsAnObject() throws Exception {
        JsonNode envelope = objectMapper.readTree(codec.encode(row().build()));

        assertEquals(MESSAGE_ID.toString(), envelope.get("messageId").asText());
        assertEquals("StartDiagnosis", envelope.get("type").asText());
        assertEquals(1, envelope.get("version").asInt());
        assertEquals("2026-10-16T12:00:00Z", envelope.get("occurredAt").asText());
        assertEquals(SAGA_ID.toString(), envelope.get("sagaId").asText());
        assertEquals(42L, envelope.get("workOrderId").asLong());
        assertEquals("EVIDENCIA-B3-001", envelope.get("correlationId").asText());
        assertTrue(envelope.get("payload").isObject());
        assertEquals("ABC1D23", envelope.get("payload").get("vehiclePlate").asText());
    }

    @Test
    void encode_omitsCausationAndCorrelationWhenThereAreNone() throws Exception {
        JsonNode envelope = objectMapper.readTree(
                codec.encode(row().correlationId(null).causationId(null).build()));

        assertFalse(envelope.has("correlationId"));
        assertFalse(envelope.has("causationId"));
    }

    @Test
    void encode_writesCausationWhenThePayloadWasCausedByAnotherMessage() throws Exception {
        UUID causationId = UUID.randomUUID();

        JsonNode envelope = objectMapper.readTree(codec.encode(row().causationId(causationId).build()));

        assertEquals(causationId.toString(), envelope.get("causationId").asText());
    }

    @Test
    void encode_rejectsAPayloadThatIsNotJson() {
        SagaOutboxJpaEntity broken = row().payload("not json").build();

        assertThrows(DomainException.class, () -> codec.encode(broken));
    }

    @Test
    void decode_readsTheEnvelopeBack() {
        JsonNode envelope = codec.decode(codec.encode(row().build()));

        assertEquals("StartDiagnosis", envelope.get("type").asText());
        assertEquals(42L, envelope.get("workOrderId").asLong());
    }

    @Test
    void decode_rejectsABodyThatIsNotJson() {
        assertThrows(DomainException.class, () -> codec.decode("not json"));
    }
}
