package br.com.fiap.postech.carworkshop.workorder.infrastructure.config;

import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaOrchestratorUseCase;
import br.com.fiap.postech.carworkshop.workorder.usecase.interactor.WorkOrderInteractor;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderUseCase;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.AutoServiceDataPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.BranchDataPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.CorrelationIdPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.CustomerDataPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.InventoryDataPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.VehicleDataPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderHistoryPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderMetricsPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderNotificationPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class WorkOrderModuleConfig {

    @Produces
    @ApplicationScoped
    public WorkOrderUseCase workOrderUseCase(WorkOrderRepositoryPort workOrderRepository,
                                             CustomerDataPort customerDataPort,
                                             VehicleDataPort vehicleDataPort,
                                             BranchDataPort branchDataPort,
                                             AutoServiceDataPort autoServiceDataPort,
                                             InventoryDataPort inventoryDataPort,
                                             WorkOrderHistoryPort historyPort,
                                             WorkOrderNotificationPort notificationPort,
                                             WorkOrderMetricsPort metricsPort,
                                             SagaOrchestratorUseCase saga,
                                             CorrelationIdPort correlationIdPort) {
        return new WorkOrderInteractor(workOrderRepository, customerDataPort, vehicleDataPort,
                branchDataPort, autoServiceDataPort, inventoryDataPort, historyPort,
                notificationPort, metricsPort, saga, correlationIdPort);
    }
}
