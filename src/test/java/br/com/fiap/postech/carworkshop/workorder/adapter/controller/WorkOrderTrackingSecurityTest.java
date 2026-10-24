package br.com.fiap.postech.carworkshop.workorder.adapter.controller;

import br.com.fiap.postech.carworkshop.shared.domain.exception.ForbiddenException;
import br.com.fiap.postech.carworkshop.utils.TokenUtils;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.when;

@QuarkusTest
class WorkOrderTrackingSecurityTest {

    private static final String OWNER_CPF = "52998224725";
    private static final String INTRUDER_CPF = "11144477735";

    @InjectMock
    WorkOrderUseCase useCase;

    private WorkOrder pendingWorkOrder;

    @BeforeEach
    void setUp() {
        pendingWorkOrder = WorkOrder.create(1L, "joao@test.com", OWNER_CPF, 1L, "ABC1234",
                "SP-01", "Barulho no motor");
        pendingWorkOrder.setId(1L);
        pendingWorkOrder.setStatus(StatusWO.PENDING_APPROVAL);
    }

    @Test
    void getStatus_withoutToken_isUnauthorized() {
        given().contentType(MediaType.APPLICATION_JSON)
                .when().get("/tracking/1")
                .then().statusCode(401);
    }

    @Test
    void getStatus_withGarbageToken_isUnauthorized() {
        given().contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer not-a-jwt")
                .when().get("/tracking/1")
                .then().statusCode(401);
    }

    @Test
    void getStatus_withUserRole_isForbidden() {
        given().contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + TokenUtils.generateUserToken())
                .when().get("/tracking/1")
                .then().statusCode(403);
    }

    @Test
    void getStatus_asTheOwner_passesTheCpfFromTheTokenToTheOwnershipCheck() {
        when(useCase.findForOwner(1L, OWNER_CPF)).thenReturn(pendingWorkOrder);

        given().contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + TokenUtils.generateCustomerToken(OWNER_CPF))
                .when().get("/tracking/1")
                .then().statusCode(200).body("id", is(1)).body("status", is("PENDING_APPROVAL"))
                .body("branch_id", is("SP-01"));

        Mockito.verify(useCase).findForOwner(1L, OWNER_CPF);
        Mockito.verify(useCase, Mockito.never()).findForCustomer(Mockito.anyLong());
    }

    @Test
    void getStatus_asAnotherCustomer_isForbidden() {
        when(useCase.findForOwner(1L, INTRUDER_CPF))
                .thenThrow(new ForbiddenException("This work order belongs to another customer."));

        given().contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + TokenUtils.generateCustomerToken(INTRUDER_CPF))
                .when().get("/tracking/1")
                .then().statusCode(403);
    }

    @Test
    void getStatus_withAdminToken_skipsTheOwnershipCheck() {
        when(useCase.findForCustomer(1L)).thenReturn(pendingWorkOrder);

        given().contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + TokenUtils.generateAdminToken())
                .when().get("/tracking/1")
                .then().statusCode(200).body("id", is(1));

        Mockito.verify(useCase).findForCustomer(1L);
        Mockito.verify(useCase, Mockito.never()).findForOwner(Mockito.anyLong(), Mockito.anyString());
    }

    @Test
    void getStatus_withACustomerTokenWithoutCpfClaim_isForbidden() {
        given().contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + TokenUtils.generateTokenString("sem-cpf", "CUSTOMER"))
                .when().get("/tracking/1")
                .then().statusCode(403);
    }

    @Test
    void approveAndReject_areGoneFromThisService() {
        given().contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + TokenUtils.generateCustomerToken(OWNER_CPF))
                .when().post("/tracking/1/approve")
                .then().statusCode(404);

        given().contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + TokenUtils.generateCustomerToken(OWNER_CPF))
                .when().post("/tracking/1/reject")
                .then().statusCode(404);
    }
}
