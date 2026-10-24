package br.com.fiap.postech.carworkshop.workorder.domain.entity;

import br.com.fiap.postech.carworkshop.autoservice.domain.entity.AutoService;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ConflictException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.InvalidOperationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrderEntityTest {

    private static final String DOCUMENT = "12345678901";
    private static final String BRANCH = "SP-01";

    private static final AutoService OIL_CHANGE = AutoService.builder()
            .id(1L).description("Troca de oleo").price(new BigDecimal("150.00")).build();
    private static final PartsAndSupply OIL_FILTER = PartsAndSupply.builder()
            .id(1L).description("Filtro de oleo").price(new BigDecimal("35.90")).quantity(2).build();

    private static WorkOrder newWorkOrder() {
        return WorkOrder.create(1L, "joao@test.com", DOCUMENT, 1L, "ABC-1234", BRANCH, "Barulho no motor");
    }

    private static WorkOrder workOrderAt(StatusWO status) {
        WorkOrder workOrder = newWorkOrder();
        workOrder.setStatus(status);
        return workOrder;
    }

    private static WorkOrder priced() {
        WorkOrder workOrder = newWorkOrder();
        workOrder.startDiagnosis();
        workOrder.priceDiagnosis("Vazamento", List.of(OIL_CHANGE), List.of(OIL_FILTER), new BigDecimal("185.90"));
        return workOrder;
    }

    @Test
    void create_opensAtTheBranchWithTheComplaintAndNoItems() {
        LocalDateTime before = LocalDateTime.now();
        WorkOrder workOrder = newWorkOrder();

        assertEquals(StatusWO.RECEIVED, workOrder.getStatus());
        assertEquals(BRANCH, workOrder.getBranchId());
        assertEquals("Barulho no motor", workOrder.getComplaint());
        assertEquals(DOCUMENT, workOrder.getCustomerDocument());
        assertTrue(workOrder.getServices().isEmpty());
        assertTrue(workOrder.getParts().isEmpty());
        assertNull(workOrder.getBudgetValue());
        assertNull(workOrder.getEndDate());
        assertNotNull(workOrder.getCreationDate());
        assertTrue(workOrder.getCreationDate().isAfter(before.minusSeconds(1)));
    }

    @Test
    void startDiagnosis_advancesOnlyFromReceived() {
        WorkOrder workOrder = newWorkOrder();

        assertTrue(workOrder.startDiagnosis());
        assertEquals(StatusWO.UNDER_DIAGNOSIS, workOrder.getStatus());
        assertFalse(workOrder.startDiagnosis());
        assertEquals(StatusWO.UNDER_DIAGNOSIS, workOrder.getStatus());
    }

    @Test
    void sagaDrivenTransitions_areNoOpFromAnyOtherStatus() {
        for (StatusWO status : StatusWO.values()) {
            if (status != StatusWO.RECEIVED) {
                assertFalse(workOrderAt(status).startDiagnosis(), "startDiagnosis from " + status);
            }
            if (status != StatusWO.UNDER_DIAGNOSIS) {
                assertFalse(workOrderAt(status).pendApproval(), "pendApproval from " + status);
            }
            if (status != StatusWO.PENDING_APPROVAL) {
                assertFalse(workOrderAt(status).awaitPayment(), "awaitPayment from " + status);
            }
            if (status != StatusWO.AWAITING_PAYMENT) {
                assertFalse(workOrderAt(status).startRepair(), "startRepair from " + status);
            }
            if (status != StatusWO.IN_PROGRESS) {
                assertFalse(workOrderAt(status).completeService(), "completeService from " + status);
            }
        }
    }

    @Test
    void priceDiagnosis_freezesDescriptionItemsAndTotal() {
        WorkOrder workOrder = priced();

        assertEquals("Vazamento", workOrder.getDiagnosticDescription());
        assertEquals(new BigDecimal("185.90"), workOrder.getBudgetValue());
        assertEquals(1, workOrder.getServices().size());
        assertEquals(1, workOrder.getParts().size());
        assertEquals(StatusWO.UNDER_DIAGNOSIS, workOrder.getStatus());
    }

    @Test
    void priceDiagnosis_isANoOpWhenAlreadyPriced() {
        WorkOrder workOrder = priced();

        assertFalse(workOrder.priceDiagnosis("Outro", List.of(), List.of(), new BigDecimal("9.99")));
        assertEquals(new BigDecimal("185.90"), workOrder.getBudgetValue());
        assertEquals("Vazamento", workOrder.getDiagnosticDescription());
    }

    @Test
    void priceDiagnosis_isANoOpBeforeTheDiagnosisStarts() {
        WorkOrder workOrder = newWorkOrder();

        assertFalse(workOrder.priceDiagnosis("Vazamento", List.of(), List.of(), BigDecimal.TEN));
        assertNull(workOrder.getBudgetValue());
    }

    @Test
    void priceDiagnosis_acceptsNullItemLists() {
        WorkOrder workOrder = newWorkOrder();
        workOrder.startDiagnosis();

        assertTrue(workOrder.priceDiagnosis("Sem itens", null, null, BigDecimal.ZERO));
        assertTrue(workOrder.getServices().isEmpty());
        assertTrue(workOrder.getParts().isEmpty());
    }

    @Test
    void happyPath_goesThroughAwaitingPaymentBeforeTheRepair() {
        WorkOrder workOrder = priced();

        assertTrue(workOrder.pendApproval());
        assertEquals(StatusWO.PENDING_APPROVAL, workOrder.getStatus());

        assertTrue(workOrder.awaitPayment());
        assertEquals(StatusWO.AWAITING_PAYMENT, workOrder.getStatus());
        assertNotNull(workOrder.getBudgetApprovalDate());

        assertTrue(workOrder.startRepair());
        assertEquals(StatusWO.IN_PROGRESS, workOrder.getStatus());

        assertTrue(workOrder.completeService());
        assertEquals(StatusWO.COMPLETED, workOrder.getStatus());
        assertNotNull(workOrder.getEndDate());

        workOrder.deliverVehicle();
        assertEquals(StatusWO.DELIVERED, workOrder.getStatus());
    }

    @Test
    void cancel_recordsTheReasonFromAnyStateBeforeThePivot() {
        for (StatusWO status : List.of(StatusWO.RECEIVED, StatusWO.UNDER_DIAGNOSIS,
                StatusWO.PENDING_APPROVAL, StatusWO.AWAITING_PAYMENT, StatusWO.IN_PROGRESS)) {
            WorkOrder workOrder = workOrderAt(status);

            assertTrue(workOrder.cancel(CancelReason.CANCELED_BY_STAFF), "cancel from " + status);
            assertEquals(StatusWO.CANCELED, workOrder.getStatus());
            assertEquals(CancelReason.CANCELED_BY_STAFF, workOrder.getCancelReason());
            assertNotNull(workOrder.getEndDate());
        }
    }

    @Test
    void cancel_isIdempotent() {
        WorkOrder workOrder = newWorkOrder();
        workOrder.cancel(CancelReason.DIAGNOSIS_FAILED);

        assertFalse(workOrder.cancel(CancelReason.CANCELED_BY_STAFF));
        assertEquals(CancelReason.DIAGNOSIS_FAILED, workOrder.getCancelReason());
    }

    @Test
    void cancel_afterThePivot_isRefused() {
        for (StatusWO status : List.of(StatusWO.COMPLETED, StatusWO.DELIVERED)) {
            WorkOrder workOrder = workOrderAt(status);

            assertThrows(ConflictException.class, () -> workOrder.cancel(CancelReason.CANCELED_BY_STAFF));
            assertEquals(status, workOrder.getStatus());
            assertNull(workOrder.getCancelReason());
        }
    }

    @Test
    void isPastPivot_isTrueOnlyAfterTheRepairIsComplete() {
        assertFalse(workOrderAt(StatusWO.IN_PROGRESS).isPastPivot());
        assertTrue(workOrderAt(StatusWO.COMPLETED).isPastPivot());
        assertTrue(workOrderAt(StatusWO.DELIVERED).isPastPivot());
    }

    @Test
    void deliverVehicle_requiresACompletedRepair() {
        InvalidOperationException error = assertThrows(InvalidOperationException.class,
                () -> newWorkOrder().deliverVehicle());

        assertEquals("Vehicle can only be delivered when status is COMPLETED.", error.getMessage());
    }

    @Test
    void softDelete_onlyAfterTheServiceIsOver() {
        WorkOrder open = newWorkOrder();
        assertThrows(InvalidOperationException.class, open::softDelete);

        WorkOrder delivered = workOrderAt(StatusWO.DELIVERED);
        delivered.softDelete();
        assertTrue(delivered.isDeleted());
    }

    @Test
    void isOwnedBy_comparesTheCustomerDocument() {
        WorkOrder workOrder = newWorkOrder();

        assertTrue(workOrder.isOwnedBy(DOCUMENT));
        assertFalse(workOrder.isOwnedBy("98765432100"));
        assertFalse(workOrder.isOwnedBy(null));
    }

    @Test
    void isOwnedBy_isFalseWhenTheWorkOrderHasNoDocument() {
        WorkOrder workOrder = newWorkOrder();
        workOrder.setCustomerDocument(null);

        assertFalse(workOrder.isOwnedBy(DOCUMENT));
    }
}
