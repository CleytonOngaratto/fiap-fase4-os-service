package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaInboxJpaEntity;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaInboxPanacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaInboxGatewayTest {

    private static final UUID MESSAGE_ID = UUID.fromString("0b8f1c2e-0000-4000-8000-000000000001");

    private final SagaInboxPanacheRepository repository = mock(SagaInboxPanacheRepository.class);
    private SagaInboxGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new SagaInboxGateway();
        gateway.repository = repository;
    }

    @Test
    void recordIfNew_marksAMessageNeverSeenBefore() {
        when(repository.findById(MESSAGE_ID)).thenReturn(null);

        assertTrue(gateway.recordIfNew(MESSAGE_ID, 42L, "DiagnosisStarted"));

        ArgumentCaptor<SagaInboxJpaEntity> captor = ArgumentCaptor.forClass(SagaInboxJpaEntity.class);
        verify(repository).persist(captor.capture());
        assertEquals(MESSAGE_ID, captor.getValue().messageId);
        assertEquals(42L, captor.getValue().getWorkOrderId());
        assertEquals("DiagnosisStarted", captor.getValue().getType());
        assertNotNull(captor.getValue().getProcessedAt());
    }

    @Test
    void recordIfNew_onARedeliveredMessage_writesNothing() {
        when(repository.findById(MESSAGE_ID))
                .thenReturn(SagaInboxJpaEntity.builder().messageId(MESSAGE_ID).build());

        assertFalse(gateway.recordIfNew(MESSAGE_ID, 42L, "DiagnosisStarted"));

        verify(repository, never()).persist(any(SagaInboxJpaEntity.class));
    }
}
