package br.com.fiap.postech.carworkshop.saga.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaOutboxJpaEntity;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaOutboxPanacheRepository;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(SagaMessagingTestProfile.class)
@QuarkusTestResource(value = SagaMessagingResource.class, restrictToAnnotatedClass = true)
class SagaMessagingIntegrationTest {

    private static final String TRACE_ID = "EVIDENCIA-B3-001";
    private static final UUID SAGA_ID = UUID.fromString("6f1d0000-0000-4000-8000-000000000002");

    @Inject
    SqsClient sqs;

    @Inject
    SagaOutboxPanacheRepository outbox;

    @Inject
    EntityManager entityManager;

    @Inject
    OutboxRelayService relay;

    @Inject
    SagaRepliesConsumer consumer;

    @ConfigProperty(name = "saga.commands.execution.queue-url")
    String commandsQueueUrl;

    @ConfigProperty(name = "saga.replies.queue-url")
    String repliesQueueUrl;

    @BeforeEach
    void emptyTheQueues() {
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(commandsQueueUrl).build());
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(repliesQueueUrl).build());
    }

    private long openWorkOrder(String traceId) {
        return given()
                .header("X-Trace-Id", traceId)
                .contentType("application/json")
                .body("{\"customer_id\":1,\"vehicle_id\":1,\"branch_id\":\"SP-01\","
                        + "\"complaint\":\"Barulho no motor\"}")
                .when().post("/work-orders")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private void sendReply(String body, String groupId) {
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(repliesQueueUrl)
                .messageBody(body)
                .messageGroupId(groupId)
                .messageDeduplicationId(UUID.randomUUID().toString())
                .build());
    }

    private static String diagnosisStarted(long workOrderId) {
        return """
                {
                  "messageId": "%s",
                  "type": "DiagnosisStarted",
                  "version": 1,
                  "occurredAt": "2026-10-16T12:00:00Z",
                  "sagaId": "%s",
                  "workOrderId": %d,
                  "correlationId": "%s",
                  "payload": { "startedAt": "2026-10-16T12:00:00Z" }
                }
                """.formatted(UUID.randomUUID(), SAGA_ID, workOrderId, TRACE_ID);
    }

    private int messagesLeftOn(String queueUrl) {
        Map<QueueAttributeName, String> attributes = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                .build()).attributes();
        return Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                + Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
    }

    // Projecao escalar, nao a entidade: uma leitura por entidade voltaria do cache de primeiro nivel com o
    // valor de antes da transacao do relay/consumidor, e o teste mentiria nas duas direcoes.
    private StatusWO statusOf(long workOrderId) {
        return entityManager
                .createQuery("select w.status from WorkOrderJpaEntity w where w.id = :id", StatusWO.class)
                .setParameter("id", workOrderId)
                .getSingleResult();
    }

    private Instant sentAtOf(UUID messageId) {
        return entityManager
                .createQuery("select o.sentAt from SagaOutboxJpaEntity o where o.messageId = :id", Instant.class)
                .setParameter("id", messageId)
                .getSingleResult();
    }

    private long pendingRowsFor(long workOrderId) {
        return entityManager
                .createQuery("select count(o) from SagaOutboxJpaEntity o "
                        + "where o.workOrderId = :id and o.sentAt is null", Long.class)
                .setParameter("id", workOrderId)
                .getSingleResult();
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void openingAWorkOrder_writesTheOutboxRowWithTheCorrelationIdFromTheHeader() {
        long workOrderId = openWorkOrder(TRACE_ID);

        SagaOutboxJpaEntity row = outbox.find("workOrderId", workOrderId).firstResult();
        assertNotNull(row);
        assertEquals("StartDiagnosis", row.getType());
        assertEquals(TRACE_ID, row.getCorrelationId());
        assertEquals("SP-01", row.getBranchId());
        assertEquals("EXECUTION", row.getDestination());
        assertEquals("{\"vehiclePlate\":\"ABC-1234\",\"complaint\":\"Barulho no motor\"}", row.getPayload());
        assertNull(row.getSentAt());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void relay_sendsTheCommandWithTheCatalogAttributesAndMarksTheRowSent() {
        long workOrderId = openWorkOrder(TRACE_ID);
        SagaOutboxJpaEntity pending = outbox.find("workOrderId", workOrderId).firstResult();

        relay.drain();

        List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(commandsQueueUrl)
                .maxNumberOfMessages(10)
                .messageAttributeNames("All")
                .attributeNamesWithStrings("MessageGroupId")
                .build()).messages();

        Message command = messages.stream()
                .filter(m -> m.body().contains("\"workOrderId\":" + workOrderId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no command for work order " + workOrderId));

        Map<String, MessageAttributeValue> attributes = command.messageAttributes();
        assertEquals("StartDiagnosis", attributes.get("type").stringValue());
        assertEquals("1", attributes.get("version").stringValue());
        assertEquals(pending.getSagaId().toString(), attributes.get("sagaId").stringValue());
        assertEquals(TRACE_ID, attributes.get("correlationId").stringValue());
        assertTrue(command.body().contains("\"vehiclePlate\":\"ABC-1234\""));

        assertNotNull(sentAtOf(pending.messageId));
        assertEquals(0L, pendingRowsFor(workOrderId));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void diagnosisStartedReply_movesTheWorkOrderToUnderDiagnosis() {
        long workOrderId = openWorkOrder(TRACE_ID);
        assertEquals(StatusWO.RECEIVED, statusOf(workOrderId));

        sendReply(diagnosisStarted(workOrderId), String.valueOf(workOrderId));
        consumer.poll();

        assertEquals(StatusWO.UNDER_DIAGNOSIS, statusOf(workOrderId));
        assertEquals(0, messagesLeftOn(repliesQueueUrl));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void theSameReplyTwice_advancesOnceAndGoesNowhereNearTheDeadLetterQueue() {
        long workOrderId = openWorkOrder(TRACE_ID);

        sendReply(diagnosisStarted(workOrderId), String.valueOf(workOrderId));
        consumer.poll();
        sendReply(diagnosisStarted(workOrderId), String.valueOf(workOrderId));
        consumer.poll();

        assertEquals(StatusWO.UNDER_DIAGNOSIS, statusOf(workOrderId));
        assertEquals(0, messagesLeftOn(repliesQueueUrl));
    }

    @Test
    void replyForAWorkOrderThatDoesNotExist_staysForTheRedrivePolicy() {
        sendReply(diagnosisStarted(999_999L), "999999");

        consumer.poll();

        assertEquals(1, messagesLeftOn(repliesQueueUrl));
    }
}
