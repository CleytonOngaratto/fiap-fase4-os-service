package br.com.fiap.postech.carworkshop.workorder.adapter.controller;

import br.com.fiap.postech.carworkshop.workorder.adapter.gateway.WorkOrderRepositoryGateway;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class WorkOrderDeliveryIntegrationTest {

    @Inject
    WorkOrderRepositoryGateway gateway;

    // Sem @TestTransaction de proposito: o endpoint roda sem transacao aberta pelo chamador, e a porta do
    // historico e MANDATORY. Abrir uma transacao aqui esconderia a falha que este teste existe para pegar.
    @Test
    @TestSecurity(user = "atendimento", roles = "ADMIN")
    void deliveringRecordsTheTransitionInTheSameTransaction() {
        WorkOrder completed = gateway.save(WorkOrder.builder()
                .customerId(1L)
                .customerDocument("12345678901")
                .customerEmail("john.silva@email.com")
                .vehicleId(3L)
                .vehiclePlate("DEF9G12")
                .branchId("SP-01")
                .complaint("Revisao concluida, aguardando retirada")
                .status(StatusWO.COMPLETED)
                .creationDate(LocalDateTime.now())
                .build());

        given()
                .when().patch("/work-orders/{id}/deliver", completed.getId())
                .then().statusCode(200)
                .body("status", is("DELIVERED"));

        given()
                .when().get("/work-orders/{id}/history", completed.getId())
                .then().statusCode(200)
                .body("status", hasItem("DELIVERED"));
    }
}
