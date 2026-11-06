package br.com.fiap.postech.carworkshop.saga.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.saga.infrastructure.messaging.SagaDeadlineScheduler.SagaDeadlineRunner;
import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaOrchestratorUseCase;
import br.com.fiap.postech.carworkshop.shared.infrastructure.observability.TraceContext;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SagaDeadlineSchedulerTest {

    @Test
    void scansWithTheConfiguredBatchSize() {
        SagaDeadlineRunner runner = mock(SagaDeadlineRunner.class);
        SagaDeadlineScheduler scheduler = new SagaDeadlineScheduler();
        scheduler.runner = runner;
        scheduler.batchSize = 10;

        scheduler.scan();

        verify(runner).expire(10);
    }

    @Test
    void namesTheTransactionOfTheScanBeforeExpiringTheDeadlines() {
        SagaOrchestratorUseCase orchestrator = mock(SagaOrchestratorUseCase.class);
        TraceContext trace = mock(TraceContext.class);
        SagaDeadlineRunner runner = new SagaDeadlineRunner();
        runner.orchestrator = orchestrator;
        runner.trace = trace;

        runner.expire(10);

        InOrder order = inOrder(trace, orchestrator);
        order.verify(trace).nameTransaction("Scheduled", "saga-deadline-scan");
        order.verify(orchestrator).expireDeadlines(10);
    }
}
