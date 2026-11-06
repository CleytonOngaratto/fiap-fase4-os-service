package br.com.fiap.postech.carworkshop.saga.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaOrchestratorUseCase;
import br.com.fiap.postech.carworkshop.shared.infrastructure.observability.TraceContext;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@ApplicationScoped
public class SagaDeadlineScheduler {

    private static final String IDENTITY = "saga-deadline-scan";
    private static final String TRANSACTION_CATEGORY = "Scheduled";

    @Inject
    SagaDeadlineRunner runner;

    @ConfigProperty(name = "saga.deadline.batch-size")
    int batchSize;

    // Metodo agendado devolve void: o Quarkus so aceita void ou CompletionStage, e o erro aponta uma classe
    // sem relacao nenhuma.
    @Scheduled(every = "{saga.deadline.scan-interval}", identity = IDENTITY,
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void scan() {
        runner.expire(batchSize);
    }

    // Bean separado: autoinvocacao nao passa pelo interceptador e o @Transactional seria ignorado em silencio.
    @ApplicationScoped
    public static class SagaDeadlineRunner {

        @Inject
        SagaOrchestratorUseCase orchestrator;

        @Inject
        TraceContext trace;

        @Transactional
        public void expire(int batchSize) {
            // O agente (modulo ejb-4.0) abre uma transacao no @Transactional a cada ciclo, com o nome .../proceed.
            trace.nameTransaction(TRANSACTION_CATEGORY, IDENTITY);
            orchestrator.expireDeadlines(batchSize);
        }
    }
}
