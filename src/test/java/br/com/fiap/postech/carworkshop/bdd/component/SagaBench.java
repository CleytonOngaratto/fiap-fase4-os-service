package br.com.fiap.postech.carworkshop.bdd.component;

import br.com.fiap.postech.carworkshop.saga.infrastructure.messaging.OutboxRelayService;
import br.com.fiap.postech.carworkshop.saga.infrastructure.messaging.SagaDeadlineScheduler;
import br.com.fiap.postech.carworkshop.saga.infrastructure.messaging.SagaRepliesConsumer;
import br.com.fiap.postech.carworkshop.saga.infrastructure.messaging.SagaReplyHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.arc.Unremovable;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Unremovable
@ApplicationScoped
public class SagaBench {

    static final List<String> COMPENSATIONS =
            List.of("CancelDiagnosis", "CancelBudget", "CancelPayment", "RefundPayment");

    private static final String REPLIES_DLQ = "os-saga-replies-dlq.fifo";
    private static final String EXECUTION_DLQ = "execution-commands-dlq.fifo";
    private static final String BILLING_DLQ = "billing-commands-dlq.fifo";

    @Inject
    OutboxRelayService relay;

    @Inject
    SagaRepliesConsumer consumer;

    @Inject
    SagaReplyHandler handler;

    @Inject
    SagaDeadlineScheduler deadlines;

    @Inject
    SqsClient sqs;

    @Inject
    EntityManager entityManager;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "saga.commands.execution.queue-url")
    String executionQueueUrl;

    @ConfigProperty(name = "saga.commands.billing.queue-url")
    String billingQueueUrl;

    @ConfigProperty(name = "saga.replies.queue-url")
    String repliesQueueUrl;

    String executionQueueUrl() {
        return executionQueueUrl;
    }

    String billingQueueUrl() {
        return billingQueueUrl;
    }

    ObjectNode newObject() {
        return mapper.createObjectNode();
    }

    void drainOutbox() {
        relay.drain();
    }

    void pollReplies() {
        consumer.poll();
    }

    void scanDeadlines() {
        deadlines.scan();
    }

    // Abre a transacao da resposta, deixa a varredura comitar antes dela e so entao comita: em uma thread so, e a
    // ordem que a corrida entre prazo e evento produz em producao. O finally evita vazar a transacao para o proximo
    // cenario quando a falha acontece antes do commit.
    void commitAfterTheDeadlineScan(String envelope) {
        QuarkusTransaction.begin();
        try {
            handler.apply(parse(envelope));
            QuarkusTransaction.requiringNew().run(this::scanDeadlines);
            QuarkusTransaction.commit();
        } finally {
            if (QuarkusTransaction.isActive()) {
                QuarkusTransaction.rollback();
            }
        }
    }

    JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corpo de mensagem invalido: " + body, e);
        }
    }

    void purgeQueues() {
        for (String url : List.of(executionQueueUrl, billingQueueUrl, repliesQueueUrl,
                dlqUrl(EXECUTION_DLQ), dlqUrl(BILLING_DLQ), dlqUrl(REPLIES_DLQ))) {
            sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(url).build());
        }
    }

    List<Message> receiveCommands(String queueUrl) {
        return sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .build()).messages();
    }

    void delete(String queueUrl, Message message) {
        sqs.deleteMessage(request -> request.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
    }

    void sendReply(String body, long workOrderId) {
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(repliesQueueUrl)
                .messageBody(body)
                .messageGroupId(String.valueOf(workOrderId))
                .messageDeduplicationId(UUID.randomUUID().toString())
                .build());
    }

    int visibleReplies() {
        return depth(repliesQueueUrl, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
    }

    int waitingOn(String queueUrl) {
        return depth(queueUrl, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
    }

    int deadLettered() {
        return depth(dlqUrl(REPLIES_DLQ), QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                + depth(dlqUrl(EXECUTION_DLQ), QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                + depth(dlqUrl(BILLING_DLQ), QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
    }

    Optional<Message> waitForDeadLetteredReply() {
        return sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(dlqUrl(REPLIES_DLQ))
                .maxNumberOfMessages(1)
                .waitTimeSeconds(1)
                .build()).messages().stream().findFirst();
    }

    void deleteDeadLetteredReply(Message message) {
        delete(dlqUrl(REPLIES_DLQ), message);
    }

    private int depth(String queueUrl, QueueAttributeName attribute) {
        return Integer.parseInt(sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(attribute)
                .build()).attributes().get(attribute));
    }

    private String dlqUrl(String name) {
        return sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
    }

    // Transacao nova em toda leitura: a projecao escalar nao passa pelo cache de primeiro nivel, e a sessao de
    // uma transacao anterior nao pode responder por uma escrita que aconteceu depois dela.
    <T> T read(Supplier<T> query) {
        return QuarkusTransaction.requiringNew().call(query::get);
    }

    void write(Runnable change) {
        QuarkusTransaction.requiringNew().run(change);
    }

    long pendingOutbox() {
        return read(() -> entityManager
                .createQuery("select count(o) from SagaOutboxJpaEntity o where o.sentAt is null", Long.class)
                .getSingleResult());
    }

    String sagaState(long workOrderId) {
        return read(() -> entityManager
                .createQuery("select cast(s.state as string) from SagaInstanceJpaEntity s "
                        + "where s.workOrderId = :id", String.class)
                .setParameter("id", workOrderId)
                .getSingleResult());
    }

    String pendingCompensations(long workOrderId) {
        return read(() -> entityManager
                .createQuery("select s.pendingCompensations from SagaInstanceJpaEntity s "
                        + "where s.workOrderId = :id", String.class)
                .setParameter("id", workOrderId)
                .getSingleResult());
    }

    long sagaVersion(long workOrderId) {
        return read(() -> entityManager
                .createQuery("select s.version from SagaInstanceJpaEntity s where s.workOrderId = :id", Long.class)
                .setParameter("id", workOrderId)
                .getSingleResult());
    }

    List<String> compensationsIssued(long workOrderId) {
        return read(() -> entityManager
                .createQuery("select o.type from SagaOutboxJpaEntity o where o.workOrderId = :id "
                        + "and o.type in :compensations", String.class)
                .setParameter("id", workOrderId)
                .setParameter("compensations", COMPENSATIONS)
                .getResultStream().sorted().toList());
    }

    String workOrderStatus(long workOrderId) {
        return read(() -> entityManager
                .createQuery("select cast(w.status as string) from WorkOrderJpaEntity w where w.id = :id",
                        String.class)
                .setParameter("id", workOrderId)
                .getSingleResult());
    }

    long inboxRows(UUID messageId) {
        return read(() -> entityManager
                .createQuery("select count(i) from SagaInboxJpaEntity i where i.messageId = :id", Long.class)
                .setParameter("id", messageId)
                .getSingleResult());
    }

    void rewindDeadline(long workOrderId) {
        write(() -> entityManager
                .createNativeQuery("update saga_instance set deadline_at = :past where work_order_id = :id")
                .setParameter("past", Instant.now().minusSeconds(60))
                .setParameter("id", workOrderId)
                .executeUpdate());
    }
}
