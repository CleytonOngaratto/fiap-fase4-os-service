package br.com.fiap.postech.carworkshop.workorder.adapter.presenter;

import br.com.fiap.postech.carworkshop.autoservice.domain.entity.AutoService;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrderStatusChange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerWorkOrderMapperTest {

    private WorkOrder buildWorkOrder(StatusWO status) {
        return WorkOrder.builder()
                .id(1L).customerId(1L).vehicleId(1L).vehiclePlate("ABC-1234")
                .branchId("SP-01").complaint("Barulho no motor")
                .status(status).creationDate(LocalDateTime.now())
                .diagnosticDescription("Verificar sistema de freios")
                .budgetValue(new BigDecimal("250.00"))
                .services(List.of(
                        AutoService.builder().id(1L).description("Troca de Óleo").price(new BigDecimal("150.00")).build(),
                        AutoService.builder().id(2L).description("Alinhamento").price(new BigDecimal("100.00")).build()
                ))
                .parts(List.of(
                        PartsAndSupply.builder().id(1L).description("Filtro de óleo")
                                .price(new BigDecimal("35.90")).quantity(2).build()
                ))
                .build();
    }

    @Test
    void from_mapsTheWorkOrderToTheTrackingResponse() {
        WorkOrderTrackingResponse response = WorkOrderTrackingResponse.from(
                buildWorkOrder(StatusWO.RECEIVED),
                List.of(new WorkOrderStatusChange(1L, "SP-01", StatusWO.RECEIVED, null, null, Instant.now())));

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals(StatusWO.RECEIVED, response.status());
        assertEquals("SP-01", response.branchId());
        assertEquals("ABC-1234", response.vehiclePlate());
        assertEquals("Barulho no motor", response.complaint());
        assertEquals("Verificar sistema de freios", response.diagnosticDescription());
        assertEquals(new BigDecimal("250.00"), response.budgetValue());
        assertEquals(2, response.services().size());
        assertEquals(1, response.parts().size());
        assertNull(response.cancelReason());
        assertEquals(1, response.history().size());
        assertEquals(StatusWO.RECEIVED, response.history().get(0).status());
    }

    @Test
    void from_carriesTheCancelReasonWhenTheWorkOrderIsCanceled() {
        WorkOrder canceled = buildWorkOrder(StatusWO.CANCELED);
        canceled.setCancelReason(CancelReason.REJECTED_BY_CUSTOMER);

        WorkOrderTrackingResponse response = WorkOrderTrackingResponse.from(canceled, List.of());

        assertEquals(CancelReason.REJECTED_BY_CUSTOMER, response.cancelReason());
    }

    @Test
    void from_handlesNullItemLists() {
        WorkOrder workOrder = WorkOrder.builder()
                .id(3L).customerId(3L).vehicleId(3L).vehiclePlate("GHI-9012").branchId("RJ-01")
                .status(StatusWO.RECEIVED).creationDate(LocalDateTime.now())
                .build();

        WorkOrderTrackingResponse response = WorkOrderTrackingResponse.from(workOrder, List.of());

        assertNotNull(response);
        assertEquals(3L, response.id());
        assertTrue(response.services().isEmpty());
        assertTrue(response.parts().isEmpty());
    }

    @Test
    void from_mapsEveryStatus() {
        for (StatusWO status : StatusWO.values()) {
            WorkOrderTrackingResponse response = WorkOrderTrackingResponse.from(buildWorkOrder(status), List.of());
            assertEquals(status, response.status());
        }
    }
}
