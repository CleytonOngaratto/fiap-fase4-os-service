package br.com.fiap.postech.carworkshop.workorder.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.SagaOutboxJpaEntity;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.SagaOutboxPanacheRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayServiceTest {

    private static final String QUEUE_URL = "http://localhost:9324/queue/execution-commands.fifo";

    private final SqsClient sqs = mock(SqsClient.class);
    private final SagaOutboxPanacheRepository repository = mock(SagaOutboxPanacheRepository.class);
    private OutboxRelayService relay;

    @BeforeEach
    void setUp() {
        SagaEnvelopeCodec codec = new SagaEnvelopeCodec();
        codec.objectMapper = new ObjectMapper();
        relay = new OutboxRelayService();
        relay.sqs = sqs;
        relay.repository = repository;
        relay.codec = codec;
        relay.commandsQueueUrl = QUEUE_URL;
        relay.batchSize = 10;
    }

    private static SagaOutboxJpaEntity pendingRow(String correlationId) {
        return SagaOutboxJpaEntity.builder()
                .messageId(UUID.randomUUID())
                .workOrderId(42L)
                .sagaId(UUID.randomUUID())
                .type("StartDiagnosis")
                .version(1)
                .occurredAt(Instant.parse("2026-10-16T12:00:00Z"))
                .correlationId(correlationId)
                .payload("{\"vehiclePlate\":\"ABC1D23\"}")
                .build();
    }

    private SendMessageRequest captureRequest() {
        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(captor.capture());
        return captor.getValue();
    }

    @Test
    void drain_sendsEachPendingRowGroupedByWorkOrderAndMarksItSent() {
        SagaOutboxJpaEntity row = pendingRow("EVIDENCIA-B3-001");
        when(repository.claimPending(10)).thenReturn(List.of(row));

        relay.drain();

        SendMessageRequest request = captureRequest();
        assertEquals(QUEUE_URL, request.queueUrl());
        assertEquals("42", request.messageGroupId());
        assertEquals(row.messageId.toString(), request.messageDeduplicationId());
        assertNotNull(row.getSentAt());
    }

    @Test
    void drain_carriesTheFourCatalogAttributesAndThePayloadInTheBody() {
        SagaOutboxJpaEntity row = pendingRow("EVIDENCIA-B3-001");
        when(repository.claimPending(10)).thenReturn(List.of(row));

        relay.drain();

        SendMessageRequest request = captureRequest();
        Map<String, MessageAttributeValue> attributes = request.messageAttributes();
        assertEquals(4, attributes.size());
        assertEquals("StartDiagnosis", attributes.get("type").stringValue());
        assertEquals("1", attributes.get("version").stringValue());
        assertEquals("Number", attributes.get("version").dataType());
        assertEquals(row.getSagaId().toString(), attributes.get("sagaId").stringValue());
        assertEquals("EVIDENCIA-B3-001", attributes.get("correlationId").stringValue());
        assertEquals(row.messageId.toString(), request.messageDeduplicationId());
        assertFalse(request.messageBody().contains("\"correlationId\":null"));
    }

    @Test
    void drain_omitsTheCorrelationAttributeWhenTheRowHasNone() {
        when(repository.claimPending(10)).thenReturn(List.of(pendingRow(null)));

        relay.drain();

        assertEquals(3, captureRequest().messageAttributes().size());
    }

    @Test
    void drain_doesNothingWhenThereIsNothingPending() {
        when(repository.claimPending(10)).thenReturn(List.of());

        relay.drain();

        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void drain_leavesTheRowPendingWhenTheSendFails() {
        SagaOutboxJpaEntity row = pendingRow("EVIDENCIA-B3-001");
        when(repository.claimPending(10)).thenReturn(List.of(row));
        doThrow(new IllegalStateException("SQS is down")).when(sqs).sendMessage(any(SendMessageRequest.class));

        assertThrows(IllegalStateException.class, () -> relay.drain());
        assertNull(row.getSentAt());
    }
}
