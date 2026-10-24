package br.com.fiap.postech.carworkshop.branch.adapter.controller;

import br.com.fiap.postech.carworkshop.branch.domain.entity.Branch;
import br.com.fiap.postech.carworkshop.branch.usecase.port.in.BranchUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.when;

@QuarkusTest
class BranchControllerTest {

    @InjectMock
    BranchUseCase useCase;

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    void findAll_listsTheBranchesAWorkOrderCanBeOpenedAt() {
        when(useCase.findAll()).thenReturn(List.of(
                Branch.builder().id("SP-01").name("Unidade Paulista").city("Sao Paulo").build()));

        given().when().get("/branches").then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].id", is("SP-01"))
                .body("[0].city", is("Sao Paulo"));

        Mockito.verify(useCase).findAll();
    }

    @Test
    void findAll_withoutToken_isUnauthorized() {
        given().when().get("/branches").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "customer", roles = "CUSTOMER")
    void findAll_withoutTheAdminRole_isForbidden() {
        given().when().get("/branches").then().statusCode(403);
    }
}
