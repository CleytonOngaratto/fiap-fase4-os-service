package br.com.fiap.postech.carworkshop.workorder.adapter.controller;

import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaOrchestratorUseCase;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ConflictException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.EntityNotFoundException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ValidationException;
import br.com.fiap.postech.carworkshop.workorder.adapter.dto.WorkOrderRequest;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrderStatusChange;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.OpenWorkOrderCommand;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@QuarkusTest
class WorkOrderControllerTest {

    @InjectMock
    WorkOrderUseCase useCase;

    @InjectMock
    SagaOrchestratorUseCase saga;

    private WorkOrderRequest workOrderRequest;

    @BeforeEach
    void setUp() {
        workOrderRequest = new WorkOrderRequest();
        workOrderRequest.setCustomerId(1L);
        workOrderRequest.setVehicleId(1L);
        workOrderRequest.setBranchId("SP-01");
        workOrderRequest.setComplaint("Barulho no motor");
    }

    private static WorkOrder workOrderAt(StatusWO status) {
        WorkOrder workOrder = WorkOrder.create(1L, "joao@test.com", "12345678901", 1L, "ABC-1234",
                "SP-01", "Barulho no motor");
        workOrder.setId(1L);
        workOrder.setStatus(status);
        return workOrder;
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void createWorkOrder_passesBranchAndComplaintToTheUseCase() {
        Mockito.when(useCase.create(any(OpenWorkOrderCommand.class))).thenReturn(workOrderAt(StatusWO.RECEIVED));

        given().contentType(MediaType.APPLICATION_JSON).body(workOrderRequest)
                .when().post("/work-orders").then()
                .statusCode(201).body("id", is(1)).body("status", is("RECEIVED"))
                .body("branch_id", is("SP-01"));

        ArgumentCaptor<OpenWorkOrderCommand> command = ArgumentCaptor.forClass(OpenWorkOrderCommand.class);
        Mockito.verify(useCase).create(command.capture());
        assertEquals(new OpenWorkOrderCommand(1L, 1L, "SP-01", "Barulho no motor"), command.getValue());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void createWorkOrder_withoutBranch_isRejected() {
        Mockito.when(useCase.create(any(OpenWorkOrderCommand.class)))
                .thenThrow(new ValidationException("branch_id is required."));

        WorkOrderRequest invalid = new WorkOrderRequest();
        invalid.setCustomerId(1L);
        invalid.setVehicleId(1L);
        invalid.setComplaint("Barulho no motor");

        given().contentType(MediaType.APPLICATION_JSON).body(invalid)
                .when().post("/work-orders").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void createWorkOrder_withoutComplaint_isRejected() {
        Mockito.when(useCase.create(any(OpenWorkOrderCommand.class)))
                .thenThrow(new ValidationException("complaint is required."));

        WorkOrderRequest invalid = new WorkOrderRequest();
        invalid.setCustomerId(1L);
        invalid.setVehicleId(1L);
        invalid.setBranchId("SP-01");

        given().contentType(MediaType.APPLICATION_JSON).body(invalid)
                .when().post("/work-orders").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void createWorkOrder_withAnUnknownBranch_isNotFound() {
        Mockito.when(useCase.create(any(OpenWorkOrderCommand.class)))
                .thenThrow(new EntityNotFoundException("Branch not found."));

        given().contentType(MediaType.APPLICATION_JSON).body(workOrderRequest)
                .when().post("/work-orders").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void createWorkOrder_nullBody_isRejected() {
        given().contentType(MediaType.APPLICATION_JSON).when().post("/work-orders").then().statusCode(400);
    }

    @Test
    void createWorkOrder_withoutToken_isUnauthorized() {
        given().contentType(MediaType.APPLICATION_JSON).body(workOrderRequest)
                .when().post("/work-orders").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "user", roles = "USER")
    void createWorkOrder_withoutTheAdminRole_isForbidden() {
        given().contentType(MediaType.APPLICATION_JSON).body(workOrderRequest)
                .when().post("/work-orders").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void diagnosisAndCompletion_areGoneFromThisService() {
        given().contentType(MediaType.APPLICATION_JSON)
                .when().post("/work-orders/1/complete-diagnosis").then().statusCode(404);

        given().contentType(MediaType.APPLICATION_JSON)
                .when().patch("/work-orders/1/complete").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void deliverWorkOrder_isAccepted() {
        Mockito.when(useCase.deliverWorkOrder(1L)).thenReturn(workOrderAt(StatusWO.DELIVERED));

        given().contentType(MediaType.APPLICATION_JSON)
                .when().patch("/work-orders/1/deliver").then()
                .statusCode(200).body("id", is(1)).body("status", is("DELIVERED"));

        Mockito.verify(useCase).deliverWorkOrder(1L);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void deliverWorkOrder_notFound() {
        Mockito.when(useCase.deliverWorkOrder(999L)).thenThrow(new EntityNotFoundException("Work Order not found."));

        given().contentType(MediaType.APPLICATION_JSON)
                .when().patch("/work-orders/999/deliver").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void findAll_listsTheWorkOrders() {
        Mockito.when(useCase.findAll()).thenReturn(List.of(workOrderAt(StatusWO.RECEIVED)));

        given().when().get("/work-orders").then()
                .statusCode(200).body("size()", is(1))
                .body("[0].id", is(1)).body("[0].status", is("RECEIVED"))
                .body("[0].complaint", is("Barulho no motor"));

        Mockito.verify(useCase).findAll();
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void findAllActive_listsTheWorkOrders() {
        Mockito.when(useCase.findAllActive()).thenReturn(List.of(workOrderAt(StatusWO.AWAITING_PAYMENT)));

        given().when().get("/work-orders/active").then()
                .statusCode(200).body("[0].status", is("AWAITING_PAYMENT"));

        Mockito.verify(useCase).findAllActive();
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void findById_returnsTheWorkOrder() {
        Mockito.when(useCase.findById(1L)).thenReturn(workOrderAt(StatusWO.IN_PROGRESS));

        given().when().get("/work-orders/1").then()
                .statusCode(200).body("id", is(1)).body("status", is("IN_PROGRESS"));

        Mockito.verify(useCase).findById(1L);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void findById_notFound() {
        Mockito.when(useCase.findById(999L)).thenThrow(new EntityNotFoundException("Work Order not found."));

        given().when().get("/work-orders/999").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void deleteWorkOrder_returnsNoContent() {
        given().when().delete("/work-orders/1").then().statusCode(204);

        Mockito.verify(useCase).deleteWorkOrder(1L);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void cancel_undoesTheSagaAndReturnsTheCanceledWorkOrder() {
        WorkOrder canceled = workOrderAt(StatusWO.CANCELED);
        canceled.setCancelReason(CancelReason.CANCELED_BY_STAFF);
        Mockito.when(useCase.findById(1L)).thenReturn(canceled);

        given().contentType(MediaType.APPLICATION_JSON)
                .when().post("/work-orders/1/cancel").then()
                .statusCode(200).body("status", is("CANCELED"))
                .body("cancel_reason", is("CANCELED_BY_STAFF"));

        Mockito.verify(saga).cancel(1L, CancelReason.CANCELED_BY_STAFF);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void cancel_afterThePivot_isAConflict() {
        Mockito.doThrow(new ConflictException(
                        "The repair is already complete: this saga can no longer be compensated."))
                .when(saga).cancel(1L, CancelReason.CANCELED_BY_STAFF);

        given().contentType(MediaType.APPLICATION_JSON)
                .when().post("/work-orders/1/cancel").then().statusCode(409);

        Mockito.verify(useCase, Mockito.never()).findById(Mockito.anyLong());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void cancel_forAWorkOrderWithoutASaga_isNotFound() {
        Mockito.doThrow(new EntityNotFoundException("No saga for Work Order 999."))
                .when(saga).cancel(999L, CancelReason.CANCELED_BY_STAFF);

        given().contentType(MediaType.APPLICATION_JSON)
                .when().post("/work-orders/999/cancel").then().statusCode(404);
    }

    @Test
    void cancel_withoutToken_isUnauthorized() {
        given().contentType(MediaType.APPLICATION_JSON)
                .when().post("/work-orders/1/cancel").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "customer", roles = "CUSTOMER")
    void cancel_withoutTheAdminRole_isForbidden() {
        given().contentType(MediaType.APPLICATION_JSON)
                .when().post("/work-orders/1/cancel").then().statusCode(403);

        Mockito.verify(saga, Mockito.never()).cancel(Mockito.anyLong(), Mockito.any());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void history_listsEveryTransition() {
        Mockito.when(useCase.historyOf(1L)).thenReturn(List.of(
                new WorkOrderStatusChange(1L, "SP-01", StatusWO.RECEIVED, null, null, Instant.now()),
                new WorkOrderStatusChange(1L, "SP-01", StatusWO.CANCELED,
                        CancelReason.CANCELED_BY_STAFF, null, Instant.now())));

        given().when().get("/work-orders/1/history").then()
                .statusCode(200).body("size()", is(2))
                .body("[0].status", is("RECEIVED"))
                .body("[1].status", is("CANCELED"))
                .body("[1].reason", is("CANCELED_BY_STAFF"));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void getAverageCompletionTime_returnsTheAverage() {
        Mockito.when(useCase.getAverageCompletionTimeInHours()).thenReturn(4.5);

        given().when().get("/work-orders/stats/average-completion-time").then()
                .statusCode(200).body("average_hours", is(4.5f));

        Mockito.verify(useCase).getAverageCompletionTimeInHours();
    }
}
