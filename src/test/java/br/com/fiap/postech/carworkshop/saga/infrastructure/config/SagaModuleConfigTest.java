package br.com.fiap.postech.carworkshop.saga.infrastructure.config;

import br.com.fiap.postech.carworkshop.saga.usecase.interactor.SagaOrchestrator;
import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaOrchestratorUseCase;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.PartsReservationPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaDeadlinePort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaInboxPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaInstanceRepositoryPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaOutboxPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.WorkOrderProjectionPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class SagaModuleConfigTest {

    @Test
    void sagaOrchestrator_buildsWiredInteractor() {
        SagaOrchestratorUseCase orchestrator = new SagaModuleConfig().sagaOrchestrator(
                mock(SagaInstanceRepositoryPort.class),
                mock(SagaOutboxPort.class),
                mock(SagaInboxPort.class),
                mock(WorkOrderProjectionPort.class),
                mock(PartsReservationPort.class),
                mock(SagaDeadlinePort.class));

        assertNotNull(orchestrator);
        assertInstanceOf(SagaOrchestrator.class, orchestrator);
    }
}
