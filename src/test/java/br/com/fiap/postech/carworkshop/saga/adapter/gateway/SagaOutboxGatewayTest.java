package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.saga.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.saga.domain.message.StartDiagnosisPayload;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaOutboxJpaEntity;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaOutboxPanacheRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionalException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class SagaOutboxGatewayTest {

    @Inject
    SagaOutboxGateway gateway;

    @Inject
    SagaOutboxPanacheRepository repository;

    private static SagaEnvelope envelope() {
        return SagaEnvelope.startDiagnosis(42L, UUID.randomUUID(), "SP-01", "EVIDENCIA-B3-001",
                new StartDiagnosisPayload("ABC1D23", "Barulho no motor"));
    }

    @Test
    void append_withoutATransaction_failsInsteadOfCommittingOnItsOwn() {
        SagaEnvelope envelope = envelope();

        assertThrows(TransactionalException.class, () -> gateway.append(envelope));
    }

    @Test
    @TestTransaction
    void append_insideATransaction_writesTheRowAsPending() {
        SagaEnvelope envelope = envelope();

        gateway.append(envelope);

        SagaOutboxJpaEntity row = repository.findById(envelope.messageId());
        assertNotNull(row);
        assertEquals("StartDiagnosis", row.getType());
        assertEquals(1, row.getVersion());
        assertEquals(42L, row.getWorkOrderId());
        assertEquals(envelope.sagaId(), row.getSagaId());
        assertEquals("EVIDENCIA-B3-001", row.getCorrelationId());
        assertEquals("SP-01", row.getBranchId());
        assertEquals("EXECUTION", row.getDestination());
        assertEquals("{\"vehiclePlate\":\"ABC1D23\",\"complaint\":\"Barulho no motor\"}", row.getPayload());
        assertNull(row.getSentAt());
    }
}
