package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.shared.domain.exception.DomainException;
import br.com.fiap.postech.carworkshop.saga.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.saga.domain.message.StartDiagnosisPayload;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaOutboxJpaEntity;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaOutboxPanacheRepository;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaOutboxGatewayUnitTest {

    private final SagaOutboxPanacheRepository repository = mock(SagaOutboxPanacheRepository.class);
    private SagaOutboxGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new SagaOutboxGateway();
        gateway.repository = repository;
        gateway.objectMapper = new ObjectMapper();
    }

    @Test
    void append_mapsTheEnvelopeToAPendingRow() {
        UUID sagaId = UUID.randomUUID();
        SagaEnvelope envelope = SagaEnvelope.startDiagnosis(42L, sagaId, "SP-01", "EVIDENCIA-B3-001",
                new StartDiagnosisPayload("ABC1D23", "Barulho no motor"));

        gateway.append(envelope);

        ArgumentCaptor<SagaOutboxJpaEntity> captor = ArgumentCaptor.forClass(SagaOutboxJpaEntity.class);
        verify(repository).persist(captor.capture());
        SagaOutboxJpaEntity row = captor.getValue();
        assertEquals(envelope.messageId(), row.messageId);
        assertEquals("StartDiagnosis", row.getType());
        assertEquals(1, row.getVersion());
        assertEquals(42L, row.getWorkOrderId());
        assertEquals(sagaId, row.getSagaId());
        assertEquals("EVIDENCIA-B3-001", row.getCorrelationId());
        assertEquals("SP-01", row.getBranchId());
        assertEquals("EXECUTION", row.getDestination());
        assertEquals(envelope.occurredAt(), row.getOccurredAt());
        assertEquals("{\"vehiclePlate\":\"ABC1D23\",\"complaint\":\"Barulho no motor\"}", row.getPayload());
        assertNull(row.getCausationId());
        assertNull(row.getSentAt());
    }

    @Test
    void append_failsWhenThePayloadCannotBeSerialized() throws Exception {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonMappingException(null, "boom"));
        gateway.objectMapper = failing;
        SagaEnvelope envelope = SagaEnvelope.startDiagnosis(42L, UUID.randomUUID(), "SP-01", null,
                new StartDiagnosisPayload("ABC1D23", "Barulho no motor"));

        assertThrows(DomainException.class, () -> gateway.append(envelope));
        verify(repository, never()).persist(any(SagaOutboxJpaEntity.class));
    }
}
