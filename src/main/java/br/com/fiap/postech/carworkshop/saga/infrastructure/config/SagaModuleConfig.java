package br.com.fiap.postech.carworkshop.saga.infrastructure.config;

import br.com.fiap.postech.carworkshop.saga.usecase.interactor.SagaOrchestrator;
import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaOrchestratorUseCase;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.PartsReservationPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaDeadlinePort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaInboxPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaInstanceRepositoryPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaOutboxPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.WorkOrderProjectionPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class SagaModuleConfig {

    @Produces
    @ApplicationScoped
    public SagaOrchestratorUseCase sagaOrchestrator(SagaInstanceRepositoryPort instances,
                                                    SagaOutboxPort outbox,
                                                    SagaInboxPort inbox,
                                                    WorkOrderProjectionPort workOrders,
                                                    PartsReservationPort parts,
                                                    SagaDeadlinePort deadlines) {
        return new SagaOrchestrator(instances, outbox, inbox, workOrders, parts, deadlines);
    }
}
