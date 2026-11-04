package br.com.fiap.postech.carworkshop.bdd.component;

import br.com.fiap.postech.carworkshop.bdd.SagaScenario;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleStateException;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ComponentOnlySteps {

    private static final long WORK_ORDER_WITHOUT_SAGA = 999_999_999L;
    private static final int DEAD_LETTER_ATTEMPTS = 15;
    private static final String EXECUTION_COMPENSATION = "CancelDiagnosis";

    private String undeliverableReply;
    private long versionBeforeTheRepeat;
    private RuntimeException failedCommit;

    private static ComponentSagaDriver driver() {
        return (ComponentSagaDriver) SagaScenario.driver();
    }

    private static SimulatedParticipants participants() {
        return driver().participants();
    }

    private static SagaBench bench() {
        return driver().bench();
    }

    private static long workOrder() {
        return driver().workOrderId();
    }

    @Dado("o Billing recusa criar orçamentos")
    public void theBillingRefusesToCreateBudgets() {
        participants().refuseBudgets();
    }

    @Dado("o provedor não consegue criar a cobrança")
    public void theProviderCannotCreateThePayment() {
        participants().failPaymentRequests();
    }

    @Dado("os participantes demoram a confirmar as compensações")
    public void theParticipantsTakeTheirTime() {
        participants().holdCompensations();
    }

    @Quando("os participantes confirmam as compensações")
    public void theParticipantsConfirmTheCompensations() {
        participants().releaseCompensations();
        driver().settle();
    }

    @Quando("chega uma resposta que o orquestrador não consegue processar")
    public void aReplyThatCannotBeProcessedArrives() {
        undeliverableReply = participants().envelope("DiagnosisStarted", participants().payload(),
                WORK_ORDER_WITHOUT_SAGA);
        bench().sendReply(undeliverableReply, WORK_ORDER_WITHOUT_SAGA);
    }

    @Entao("a resposta vai intacta para a DLQ depois de esgotar as tentativas")
    public void theReplyReachesTheDeadLetterQueueIntact() {
        Optional<Message> deadLettered = Optional.empty();
        for (int attempt = 0; attempt < DEAD_LETTER_ATTEMPTS && deadLettered.isEmpty(); attempt++) {
            bench().pollReplies();
            deadLettered = bench().waitForDeadLetteredReply();
        }
        Message message = deadLettered.orElseThrow(
                () -> new AssertionError("a resposta nunca chegou a DLQ: " + undeliverableReply));
        assertEquals(undeliverableReply, message.body());
        bench().deleteDeadLetteredReply(message);
    }

    @Quando("a última resposta chega de novo")
    public void theLastReplyArrivesAgain() {
        versionBeforeTheRepeat = bench().sagaVersion(workOrder());
        redeliverTheLastReply();
    }

    @Entao("a resposta repetida não tem efeito")
    public void theRepeatedReplyHasNoEffect() {
        assertEquals(1L, bench().inboxRows(participants().lastReplyId()));
        assertEquals(versionBeforeTheRepeat, bench().sagaVersion(workOrder()));
    }

    @Quando("a confirmação do pagamento perde a corrida para o prazo")
    public void theConfirmationLosesTheRace() {
        bench().rewindDeadline(workOrder());
        String confirmation = participants().paymentConfirmedEnvelope(driver().paymentIdAtTheProvider());
        failedCommit = assertThrows(RuntimeException.class, () -> bench().commitAfterTheDeadlineScan(confirmation));
    }

    @Entao("a transação da confirmação reverte por conflito de versão")
    public void theConfirmationIsRolledBackByTheVersionConflict() {
        assertTrue(isVersionConflict(failedCommit), "o commit falhou por outro motivo: " + failedCommit);
        assertEquals("COMPENSATING", bench().sagaState(workOrder()));
        assertEquals(0L, bench().inboxRows(participants().lastReplyId()));
    }

    private static boolean isVersionConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException || cause instanceof StaleStateException) {
                return true;
            }
        }
        return false;
    }

    @Quando("a confirmação do pagamento é reentregue")
    public void theConfirmationIsRedelivered() {
        redeliverTheLastReply();
    }

    private static void redeliverTheLastReply() {
        bench().sendReply(participants().lastReply(), workOrder());
        driver().settle();
    }

    @Quando("a confirmação do pagamento ganha a corrida contra o prazo")
    public void theConfirmationWinsTheRace() {
        bench().rewindDeadline(workOrder());
        driver().pay();
        bench().scanDeadlines();
        driver().settle();
    }

    @Entao("nenhuma compensação foi emitida")
    public void noCompensationWasIssued() {
        assertEquals(List.of(), bench().compensationsIssued(workOrder()));
    }

    @Entao("nenhum orçamento foi pedido")
    public void noBudgetWasRequested() {
        assertFalse(participants().billingCommands().contains("CreateBudget"));
    }

    // Conjunto, nao sequencia: a ordem de emissao esta provada no SagaOrchestratorTest. Aqui a unica ordem seria
    // occurred_at, que empata no relogio do Windows (salta a cada ~500 us; no Linux, 1 us) e tornaria o cenario instavel.
    @Entao("as compensações emitidas são {string}")
    public void theCompensationsIssuedAre(String compensations) {
        List<String> expected = Arrays.stream(compensations.split(",")).map(String::trim).sorted().toList();
        assertEquals(expected, bench().compensationsIssued(workOrder()));

        Map<Boolean, List<String>> byQueue = expected.stream()
                .collect(Collectors.partitioningBy(EXECUTION_COMPENSATION::equals));
        assertEquals(byQueue.get(true), onlyCompensations(participants().executionCommands()));
        assertEquals(byQueue.get(false), onlyCompensations(participants().billingCommands()));
    }

    private static List<String> onlyCompensations(List<String> commands) {
        return commands.stream().filter(SagaBench.COMPENSATIONS::contains).sorted().toList();
    }
}
