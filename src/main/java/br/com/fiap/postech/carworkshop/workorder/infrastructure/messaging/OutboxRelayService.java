package br.com.fiap.postech.carworkshop.workorder.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.shared.infrastructure.observability.LogContext;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.SagaOutboxJpaEntity;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.SagaOutboxPanacheRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class OutboxRelayService {

    @Inject
    SqsClient sqs;

    @Inject
    SagaOutboxPanacheRepository repository;

    @Inject
    SagaEnvelopeCodec codec;

    @ConfigProperty(name = "saga.commands.execution.queue-url")
    String commandsQueueUrl;

    @ConfigProperty(name = "saga.outbox.relay.batch-size")
    int batchSize;

    @Scheduled(every = "2s", identity = "saga-outbox-relay",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    public void drain() {
        List<SagaOutboxJpaEntity> pending = repository.claimPending(batchSize);
        for (SagaOutboxJpaEntity row : pending) {
            try (LogContext ignored = LogContext.of(row.getCorrelationId(), row.getSagaId())) {
                sqs.sendMessage(requestFor(row));
                row.setSentAt(Instant.now());
                log.info("Outbox message {} sent for Work Order {}.", row.getType(), row.getWorkOrderId());
            }
        }
    }

    private SendMessageRequest requestFor(SagaOutboxJpaEntity row) {
        return SendMessageRequest.builder()
                .queueUrl(commandsQueueUrl)
                .messageBody(codec.encode(row))
                .messageGroupId(String.valueOf(row.getWorkOrderId()))
                .messageDeduplicationId(row.messageId.toString())
                .messageAttributes(attributesFor(row))
                .build();
    }

    private Map<String, MessageAttributeValue> attributesFor(SagaOutboxJpaEntity row) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put(SagaEnvelopeCodec.FIELD_TYPE, text(row.getType()));
        attributes.put(SagaEnvelopeCodec.FIELD_VERSION, number(row.getVersion()));
        attributes.put(SagaEnvelopeCodec.FIELD_SAGA_ID, text(row.getSagaId().toString()));
        if (row.getCorrelationId() != null && !row.getCorrelationId().isBlank()) {
            attributes.put(SagaEnvelopeCodec.FIELD_CORRELATION_ID, text(row.getCorrelationId()));
        }
        return attributes;
    }

    private static MessageAttributeValue text(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private static MessageAttributeValue number(int value) {
        return MessageAttributeValue.builder().dataType("Number").stringValue(String.valueOf(value)).build();
    }
}
