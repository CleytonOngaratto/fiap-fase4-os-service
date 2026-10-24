package br.com.fiap.postech.carworkshop.saga.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.shared.infrastructure.observability.LogContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class SagaRepliesConsumer {

    @Inject
    SqsClient sqs;

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

    private void consume(Message message) {
        JsonNode envelope;
        try {
            envelope = codec.decode(message.body());
        } catch (RuntimeException e) {
            // Sem apagar: a mensagem volta, esgota o maxReceiveCount e para na DLQ, onde da para investigar.
            log.error("Saga reply could not be read; leaving it for the redrive policy.", e);
            return;
        }

        String correlationId = envelope.path(SagaEnvelopeCodec.FIELD_CORRELATION_ID).asText(null);

        try (LogContext ignored = LogContext.of(correlationId, sagaIdOf(envelope))) {
            handler.apply(envelope);
            sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(repliesQueueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (RuntimeException e) {
            log.error("Saga reply failed; leaving it for the redrive policy.", e);
        }
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
