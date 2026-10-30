package br.com.fiap.postech.carworkshop.catalog.adapter.controller;

import br.com.fiap.postech.carworkshop.shared.infrastructure.security.ServiceTokenFilter;
import br.com.fiap.postech.carworkshop.utils.TokenUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class InternalCatalogControllerTest {

    private static final String PATH = "/internal/catalog/validate";
    private static final String TOKEN = "test-service-token";

    @Test
    void validate_withEveryItemInTheCatalog_answersNothingInvalid() {
        given().header(ServiceTokenFilter.HEADER, TOKEN).contentType("application/json")
                .body("{\"items\":[{\"type\":\"SERVICE\",\"catalogId\":1},"
                        + "{\"type\":\"PART\",\"catalogId\":1}]}")
                .when().post(PATH)
                .then().statusCode(200).body("invalid.size()", is(0));
    }

    @Test
    void validate_namesTheItemTheCatalogDoesNotKnow() {
        given().header(ServiceTokenFilter.HEADER, TOKEN).contentType("application/json")
                .body("{\"items\":[{\"type\":\"PART\",\"catalogId\":1},"
                        + "{\"type\":\"PART\",\"catalogId\":9999}]}")
                .when().post(PATH)
                .then().statusCode(200)
                .body("invalid.size()", is(1))
                .body("invalid[0].catalogId", is(9999))
                .body("invalid[0].type", is("PART"));
    }

    // Um id de peca nao vale como servico: os dois catalogos sao tabelas diferentes, e confundi-los deixaria
    // passar item que nao existe.
    @Test
    void validate_doesNotAcceptAPartIdentifierAsAService() {
        given().header(ServiceTokenFilter.HEADER, TOKEN).contentType("application/json")
                .body("{\"items\":[{\"type\":\"SERVICE\",\"catalogId\":3}]}")
                .when().post(PATH)
                .then().statusCode(200).body("invalid.size()", is(1));
    }

    @Test
    void validate_withoutTheServiceToken_isUnauthorized() {
        given().contentType("application/json")
                .body("{\"items\":[{\"type\":\"PART\",\"catalogId\":1}]}")
                .when().post(PATH)
                .then().statusCode(401);
    }

    // Um JWT de ADMIN nao substitui a credencial de servico, como no endpoint interno de clientes.
    @Test
    void validate_withAnAdminTokenButNoServiceToken_isUnauthorized() {
        given().header("Authorization", "Bearer " + TokenUtils.generateAdminToken())
                .contentType("application/json")
                .body("{\"items\":[{\"type\":\"PART\",\"catalogId\":1}]}")
                .when().post(PATH)
                .then().statusCode(401);
    }

    @Test
    void validate_withoutAnyItem_isABadRequest() {
        given().header(ServiceTokenFilter.HEADER, TOKEN).contentType("application/json")
                .body("{\"items\":[]}")
                .when().post(PATH)
                .then().statusCode(400);
    }
}
