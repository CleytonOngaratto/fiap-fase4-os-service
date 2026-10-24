package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.TypeProductEnum;
import br.com.fiap.postech.carworkshop.inventory.infrastructure.persistence.PartsAndSupplyPanacheRepository;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.PartsReservationPort;
import br.com.fiap.postech.carworkshop.shared.domain.exception.StockException;
import br.com.fiap.postech.carworkshop.workorder.adapter.gateway.WorkOrderRepositoryGateway;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class PartsReservationTransactionTest {

    private static final Long PART = 3L;

    @Inject
    PartsReservationPort parts;

    @Inject
    WorkOrderRepositoryGateway workOrders;

    @Inject
    PartsAndSupplyPanacheRepository inventory;

    @Inject
    TransactionManager transactionManager;

    @Test
    @TestTransaction
    void insufficientStockLeavesTheTransactionUsableForTheCompensation() throws Exception {
        int available = inventory.findById(PART).getQuantity() - inventory.findById(PART).getReservedQuantity();
        WorkOrder workOrder = workOrderAskingFor(available + 1);

        assertThrows(StockException.class, () -> parts.reserve(workOrder.getId()));

        assertEquals(Status.STATUS_ACTIVE, transactionManager.getStatus());
    }

    private WorkOrder workOrderAskingFor(int quantity) {
        WorkOrder workOrder = workOrders.save(WorkOrder.builder()
                .customerId(1L)
                .customerDocument("12345678901")
                .customerEmail("john.silva@email.com")
                .vehicleId(3L)
                .vehiclePlate("GHI-9012")
                .branchId("SP-01")
                .complaint("Filtro de ar para a frota")
                .status(StatusWO.RECEIVED)
                .creationDate(LocalDateTime.now())
                .build());

        workOrder.startDiagnosis();
        workOrder.priceDiagnosis("Filtro de ar vencido",
                List.of(),
                List.of(PartsAndSupply.builder()
                        .id(PART).code("P003").description("Filtro de ar")
                        .price(new BigDecimal("40.00")).type(TypeProductEnum.UNITARY)
                        .quantity(quantity).build()),
                new BigDecimal("40.00").multiply(BigDecimal.valueOf(quantity)));
        return workOrders.save(workOrder);
    }
}
