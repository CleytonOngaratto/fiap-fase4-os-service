package br.com.fiap.postech.carworkshop.saga.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.shared.infrastructure.observability.LogContext;
import br.com.fiap.postech.carworkshop.shared.infrastructure.observability.TraceContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.newrelic.api.agent.Trace;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class SagaRepliesConsumer {

    private static final String TRANSACTION_CATEGORY = "Message";
    private static final String QUEUE = "os-saga-replies";

    @Inject
    SqsClient sqs;

    @Inject
    TraceContext trace;

    @Inject
    SagaEnvelopeCodec codec;

    @Inject
    SagaReplyHandler handler;

    @ConfigProperty(name = "saga.replies.queue-url")
    String repliesQueueUrl;

    @ConfigProperty(name = "saga.replies.batch-size")
    int batchSize;

    @Scheduled(every = "2s", identity = "saga-replies-consumer",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void poll() {
        List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(repliesQueueUrl)
                .maxNumberOfMessages(batchSize)
                .messageAttributeNames("All")
                .build()).messages();

        for (Message message : messages) {
            consume(message);
        }
    }

    @Trace(dispatcher = true)
    void consume(Message message) {
        trace.accept(traceHeadersOf(message));
        JsonNode envelope;
        try {
            envelope = codec.decode(message.body());
        } catch (RuntimeException e) {
            trace.nameTransaction(TRANSACTION_CATEGORY, QUEUE + "/unreadable");
            // Sem apagar: a mensagem volta, esgota o maxReceiveCount e para na DLQ, onde da para investigar.
            log.error("Saga reply could not be read; leaving it for the redrive policy.", e);
            trace.noticeError(e);
            return;
        }
        describe(envelope);

        try (LogContext ignored = LogContext.of(textOf(envelope, SagaEnvelopeCodec.FIELD_CORRELATION_ID),
                sagaIdOf(envelope), textOf(envelope, SagaEnvelopeCodec.FIELD_BRANCH_ID))) {
            apply(message, envelope);
        }
    }

    private void apply(Message message, JsonNode envelope) {
        try {
            handler.apply(envelope);
            sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(repliesQueueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (RuntimeException e) {
            log.error("Saga reply failed; leaving it for the redrive policy.", e);
            trace.noticeError(e);
        }
    }

    private void describe(JsonNode envelope) {
        String type = textOf(envelope, SagaEnvelopeCodec.FIELD_TYPE);
        trace.nameTransaction(TRANSACTION_CATEGORY, QUEUE + "/" + type);
        trace.addAttribute("messageType", type);
        trace.addAttribute(LogContext.SAGA_ID, textOf(envelope, SagaEnvelopeCodec.FIELD_SAGA_ID));
        trace.addAttribute("workOrderId", textOf(envelope, SagaEnvelopeCodec.FIELD_WORK_ORDER_ID));
        trace.addAttribute(LogContext.BRANCH_ID, textOf(envelope, SagaEnvelopeCodec.FIELD_BRANCH_ID));
    }

    private static Map<String, String> traceHeadersOf(Message message) {
        Map<String, String> headers = new HashMap<>();
        message.messageAttributes().forEach((name, value) -> headers.put(name, value.stringValue()));
        return headers;
    }

    private static String textOf(JsonNode envelope, String field) {
        return envelope.path(field).asText(null);
    }

    // O sagaId so alimenta o log: um valor malformado nao pode derrubar o processamento da mensagem.
    private static UUID sagaIdOf(JsonNode envelope) {
        String value = envelope.path(SagaEnvelopeCodec.FIELD_SAGA_ID).asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
