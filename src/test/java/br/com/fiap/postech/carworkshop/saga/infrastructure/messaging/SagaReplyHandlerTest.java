package br.com.fiap.postech.carworkshop.saga.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.saga.domain.message.SagaItem;
import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaOrchestratorUseCase;
import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaReply;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SagaReplyHandlerTest {

    private static final UUID MESSAGE_ID = UUID.fromString("0b8f1c2e-0000-4000-8000-000000000001");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SagaOrchestratorUseCase orchestrator = mock(SagaOrchestratorUseCase.class);
    private SagaReplyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SagaReplyHandler();
        handler.orchestrator = orchestrator;
    }

    private SagaReply applyAndCapture(String body) throws Exception {
        handler.apply(objectMapper.readTree(body));
        ArgumentCaptor<SagaReply> captor = ArgumentCaptor.forClass(SagaReply.class);
        verify(orchestrator).onReply(captor.capture());
        return captor.getValue();
    }

    @Test
    void readsTheEnvelopeFieldsTheOrchestratorNeeds() throws Exception {
        SagaReply reply = applyAndCapture("""
                {
                  "messageId": "0b8f1c2e-0000-4000-8000-000000000001",
                  "type": "DiagnosisStarted",
                  "workOrderId": 42,
                  "correlationId": "EVIDENCIA-B4-001",
                  "payload": { "startedAt": "2026-10-22T10:00:00Z" }
                }
                """);

        assertEquals(MESSAGE_ID, reply.messageId());
        assertEquals("DiagnosisStarted", reply.type());
        assertEquals(42L, reply.workOrderId());
        assertEquals("EVIDENCIA-B4-001", reply.correlationId());
        assertTrue(reply.items().isEmpty());
    }

    @Test
    void readsTheDiagnosedItemsWithoutTrustingAPriceFromTheParticipant() throws Exception {
        SagaReply reply = applyAndCapture("""
                {
                  "messageId": "0b8f1c2e-0000-4000-8000-000000000001",
                  "type": "DiagnosisCompleted",
                  "workOrderId": 42,
                  "payload": {
                    "description": "Vazamento no radiador",
                    "items": [
                      { "type": "SERVICE", "catalogId": 1, "quantity": 1 },
                      { "type": "PART", "catalogId": 3, "quantity": 2 }
                    ]
                  }
                }
                """);

        assertEquals("Vazamento no radiador", reply.description());
        assertEquals(2, reply.items().size());
        assertEquals(SagaItem.SERVICE, reply.items().get(0).type());
        assertEquals(2, reply.items().get(1).quantity());
        assertEquals(0, reply.items().get(1).unitPrice().signum());
    }

    @Test
    void readsTheBudgetAndPaymentIdentifiers() throws Exception {
        SagaReply reply = applyAndCapture("""
                {
                  "messageId": "0b8f1c2e-0000-4000-8000-000000000001",
                  "type": "PaymentConfirmed",
                  "workOrderId": 42,
                  "payload": { "paymentId": "MP-9988", "budgetId": "B-77" }
                }
                """);

        assertEquals("MP-9988", reply.paymentId());
        assertEquals("B-77", reply.budgetId());
    }

    @Test
    void anUnknownTypeStillReachesTheOrchestrator() throws Exception {
        SagaReply reply = applyAndCapture("""
                { "messageId": "0b8f1c2e-0000-4000-8000-000000000001",
                  "type": "SomethingElse", "workOrderId": 42 }
                """);

        assertEquals("SomethingElse", reply.type());
    }

    @Test
    void aMalformedMessageIdBecomesNullInsteadOfBreakingTheRead() throws Exception {
        SagaReply reply = applyAndCapture("""
                { "messageId": "not-a-uuid", "type": "DiagnosisStarted", "workOrderId": 42 }
                """);

        assertNull(reply.messageId());
    }
}
