package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.inventory.usecase.port.in.InventoryUseCase;
import br.com.fiap.postech.carworkshop.inventory.usecase.port.out.InventoryRepositoryPort;
import br.com.fiap.postech.carworkshop.shared.domain.exception.EntityNotFoundException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.StockException;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartsReservationGatewayTest {

    private static final Long WORK_ORDER = 42L;

    private final WorkOrderRepositoryPort workOrders = mock(WorkOrderRepositoryPort.class);
    private final InventoryRepositoryPort inventoryRepository = mock(InventoryRepositoryPort.class);
    private final InventoryUseCase inventory = mock(InventoryUseCase.class);

    private PartsReservationGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new PartsReservationGateway();
        gateway.workOrders = workOrders;
        gateway.inventoryRepository = inventoryRepository;
        gateway.inventory = inventory;
    }

    private static PartsAndSupply part(Long id, int quantity, int reserved) {
        return PartsAndSupply.builder()
                .id(id).code("P00" + id).description("Peca " + id)
                .price(new BigDecimal("10.00")).quantity(quantity).reservedQuantity(reserved)
                .build();
    }

    private void givenWorkOrderWants(PartsAndSupply... snapshot) {
        WorkOrder workOrder = WorkOrder.create(1L, "a@b.com", "12345678901", 1L, "ABC-1234",
                "SP-01", "ruido");
        workOrder.setId(WORK_ORDER);
        workOrder.setParts(List.of(snapshot));
        when(workOrders.findById(WORK_ORDER)).thenReturn(Optional.of(workOrder));
    }

    private void givenStock(PartsAndSupply... stock) {
        when(inventoryRepository.findByIds(anyList())).thenReturn(List.of(stock));
    }

    @Test
    void reserve_asksTheInventoryForTheQuantityTheDiagnosisWanted() {
        givenWorkOrderWants(part(3L, 2, 0), part(5L, 1, 0));
        givenStock(part(3L, 20, 0), part(5L, 4, 0));

        gateway.reserve(WORK_ORDER);

        verify(inventory).reserve(3L, 2);
        verify(inventory).reserve(5L, 1);
    }

    @Test
    void reserve_validatesEveryPartBeforeTouchingAnyOfThem() {
        givenWorkOrderWants(part(3L, 2, 0), part(5L, 10, 0));
        givenStock(part(3L, 20, 0), part(5L, 4, 0));

        assertThrows(StockException.class, () -> gateway.reserve(WORK_ORDER));

        verify(inventory, never()).reserve(anyLong(), anyInt());
    }

    @Test
    void reserve_doesNotCountUnitsAlreadyReservedForAnotherWorkOrder() {
        givenWorkOrderWants(part(3L, 5, 0));
        givenStock(part(3L, 10, 8));

        assertThrows(StockException.class, () -> gateway.reserve(WORK_ORDER));
        verify(inventory, never()).reserve(anyLong(), anyInt());
    }

    @Test
    void reserve_withAPartMissingFromTheCatalog_isNotFound() {
        givenWorkOrderWants(part(3L, 1, 0));
        givenStock();

        assertThrows(EntityNotFoundException.class, () -> gateway.reserve(WORK_ORDER));
    }

    @Test
    void reserve_withoutParts_doesNothing() {
        givenWorkOrderWants();

        gateway.reserve(WORK_ORDER);

        verify(inventory, never()).reserve(anyLong(), anyInt());
        verify(inventoryRepository, never()).findByIds(anyList());
    }

    @Test
    void confirmReleaseAndRestore_useTheSameSnapshotQuantities() {
        givenWorkOrderWants(part(3L, 2, 0));

        gateway.confirm(WORK_ORDER);
        gateway.release(WORK_ORDER);
        gateway.restore(WORK_ORDER);

        verify(inventory).confirmReservation(3L, 2);
        verify(inventory).releaseReservation(3L, 2);
        verify(inventory).restore(3L, 2);
    }

    @Test
    void aSnapshotPartWithoutQuantity_countsAsOne() {
        PartsAndSupply withoutQuantity = part(3L, 0, 0);
        withoutQuantity.setQuantity(null);
        givenWorkOrderWants(withoutQuantity);

        gateway.confirm(WORK_ORDER);

        verify(inventory).confirmReservation(3L, 1);
    }

    @Test
    @Timeout(5)
    void anUnknownWorkOrder_isNotFound() {
        when(workOrders.findById(WORK_ORDER)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> gateway.reserve(WORK_ORDER));
    }
}
