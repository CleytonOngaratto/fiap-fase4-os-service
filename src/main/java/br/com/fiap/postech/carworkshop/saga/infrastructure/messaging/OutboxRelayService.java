package br.com.fiap.postech.carworkshop.saga.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.saga.domain.message.SagaDestination;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaOutboxJpaEntity;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaOutboxPanacheRepository;
import br.com.fiap.postech.carworkshop.shared.infrastructure.observability.LogContext;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    Optional<String> executionQueueUrl;

    @ConfigProperty(name = "saga.commands.billing.queue-url")
    Optional<String> billingQueueUrl;

    @ConfigProperty(name = "saga.outbox.relay.batch-size")
    int batchSize;

    private final Map<SagaDestination, String> queues = new EnumMap<>(SagaDestination.class);

    void mapDestinations(@Observes StartupEvent event) {
        queues.clear();
        put(SagaDestination.EXECUTION, executionQueueUrl);
        put(SagaDestination.BILLING, billingQueueUrl);
        for (SagaDestination destination : SagaDestination.values()) {
            if (!queues.containsKey(destination)) {
                log.warn("No queue is configured for {}: its commands stay in the outbox until it is.",
                        destination);
            }
        }
    }

    @Scheduled(every = "2s", identity = "saga-outbox-relay",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    public void drain() {
        Set<String> configured = queues.keySet().stream().map(Enum::name).collect(Collectors.toSet());
        List<SagaOutboxJpaEntity> pending = repository.claimPending(configured, batchSize);
        for (SagaOutboxJpaEntity row : pending) {
            try (LogContext ignored = LogContext.of(row.getCorrelationId(), row.getSagaId())) {
                sqs.sendMessage(requestFor(row));
                row.setSentAt(Instant.now());
                log.info("Outbox message {} sent for Work Order {}.", row.getType(), row.getWorkOrderId());
            }
        }
    }

    private void put(SagaDestination destination, Optional<String> url) {
        url.filter(value -> !value.isBlank()).ifPresent(value -> queues.put(destination, value));
    }

    private SendMessageRequest requestFor(SagaOutboxJpaEntity row) {
        return SendMessageRequest.builder()
                .queueUrl(queues.get(SagaDestination.valueOf(row.getDestination())))
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
