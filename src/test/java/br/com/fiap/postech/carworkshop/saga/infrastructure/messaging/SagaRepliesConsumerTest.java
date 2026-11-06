package br.com.fiap.postech.carworkshop.saga.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.shared.infrastructure.observability.LogContext;
import br.com.fiap.postech.carworkshop.shared.infrastructure.observability.LogLines;
import br.com.fiap.postech.carworkshop.shared.infrastructure.observability.TraceContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaRepliesConsumerTest {

    private static final String QUEUE_URL = "http://localhost:9324/000000000000/os-saga-replies.fifo";
    private static final String SAGA_ID = "22222222-2222-2222-2222-222222222222";

    private SqsClient sqs;
    private SagaReplyHandler handler;
    private TraceContext trace;
    private SagaRepliesConsumer consumer;

    @BeforeEach
    void setUp() {
        sqs = mock(SqsClient.class);
        handler = mock(SagaReplyHandler.class);
        trace = mock(TraceContext.class);
        SagaEnvelopeCodec codec = new SagaEnvelopeCodec();
        codec.objectMapper = new ObjectMapper();
        consumer = new SagaRepliesConsumer();
        consumer.sqs = sqs;
        consumer.codec = codec;
        consumer.handler = handler;
        consumer.trace = trace;
        consumer.repliesQueueUrl = QUEUE_URL;
        consumer.batchSize = 10;
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void appliesAndDeletesEveryReplyOfTheBatch() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(reply("rh-1"), reply("rh-2")).build());

        consumer.poll();

        verify(handler, times(2)).apply(any(JsonNode.class));
        verify(sqs, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void leavesAFailedReplyForTheRedrivePolicy() {
        doThrow(new IllegalStateException("boom")).when(handler).apply(any(JsonNode.class));

        consumer.consume(reply("rh-1"));

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void leavesAnUnreadableReplyForTheRedrivePolicy() {
        consumer.consume(Message.builder().body("not json").receiptHandle("rh-1").build());

        verify(handler, never()).apply(any());
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void logsTheFailureWithTheContextOfTheReply() {
        doThrow(new IllegalStateException("boom")).when(handler).apply(any(JsonNode.class));

        try (LogLines lines = LogLines.of(SagaRepliesConsumer.class)) {
            consumer.consume(reply("rh-1"));

            assertEquals(List.of(Map.of(
                    LogContext.CORRELATION_ID, "EVIDENCIA-B8-001",
                    LogContext.SAGA_ID, SAGA_ID,
                    LogContext.BRANCH_ID, "SP-01")), lines.errors());
        }
    }

    @Test
    void acceptsTheTraceContextThatCameWithTheReply() {
        consumer.consume(reply("rh-1").toBuilder()
                .messageAttributes(Map.of(
                        "traceparent", text("00-trace-span-01"),
                        "tracestate", text("nr=state"),
                        "type", text("DiagnosisStarted")))
                .build());

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(trace).accept(captor.capture());
        assertEquals("00-trace-span-01", captor.getValue().get("traceparent"));
        assertEquals("nr=state", captor.getValue().get("tracestate"));
    }

    @Test
    void namesTheTransactionAfterTheQueueAndTheReplyType() {
        consumer.consume(reply("rh-1"));

        verify(trace).nameTransaction("Message", "os-saga-replies/DiagnosisStarted");
        verify(trace).addAttribute("messageType", "DiagnosisStarted");
        verify(trace).addAttribute(LogContext.SAGA_ID, SAGA_ID);
        verify(trace).addAttribute("workOrderId", "42");
        verify(trace).addAttribute(LogContext.BRANCH_ID, "SP-01");
    }

    @Test
    void namesTheTransactionOfAnUnreadableReply() {
        consumer.consume(Message.builder().body("not json").receiptHandle("rh-1").build());

        verify(trace).nameTransaction("Message", "os-saga-replies/unreadable");
    }

    @Test
    void reportsTheFailureToTheTransactionOfTheReply() {
        IllegalStateException failure = new IllegalStateException("boom");
        doThrow(failure).when(handler).apply(any(JsonNode.class));

        consumer.consume(reply("rh-1"));

        verify(trace).noticeError(failure);
    }

    @Test
    void reportsAnUnreadableReplyToItsTransaction() {
        consumer.consume(Message.builder().body("not json").receiptHandle("rh-1").build());

        verify(trace).noticeError(any(RuntimeException.class));
    }

    @Test
    void reportsNoErrorForAReplyThatWasApplied() {
        consumer.consume(reply("rh-1"));

        verify(trace, never()).noticeError(any());
    }

    @Test
    void clearsTheContextOnceTheReplyIsDone() {
        consumer.consume(reply("rh-1"));

        assertNull(MDC.get(LogContext.CORRELATION_ID));
        assertNull(MDC.get(LogContext.SAGA_ID));
        assertNull(MDC.get(LogContext.BRANCH_ID));
    }

    private static MessageAttributeValue text(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private static Message reply(String receiptHandle) {
        return Message.builder()
                .body("{\"messageId\":\"11111111-1111-1111-1111-111111111111\",\"type\":\"DiagnosisStarted\","
                        + "\"sagaId\":\"" + SAGA_ID + "\",\"branchId\":\"SP-01\","
                        + "\"correlationId\":\"EVIDENCIA-B8-001\",\"workOrderId\":42,\"payload\":{}}")
                .receiptHandle(receiptHandle)
                .build();
    }
}
