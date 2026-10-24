package br.com.fiap.postech.carworkshop.customer.adapter.controller;

import br.com.fiap.postech.carworkshop.shared.infrastructure.security.ServiceTokenFilter;
import br.com.fiap.postech.carworkshop.utils.TokenUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class InternalCustomerControllerTest {

    private static final String TOKEN = "test-service-token";
    private static final String SEEDED_CPF = "12345678901";

    @Test
    void findByDocument_withTheServiceToken_answersTheLambda() {
        given().header(ServiceTokenFilter.HEADER, TOKEN)
                .when().get("/internal/customers/" + SEEDED_CPF)
                .then().statusCode(200).body("id", is(1)).body("name", is("John Silva"));
    }

    @Test
    void findByDocument_withoutTheServiceToken_isUnauthorized() {
        given().when().get("/internal/customers/" + SEEDED_CPF)
                .then().statusCode(401);
    }

    @Test
    void findByDocument_withTheWrongServiceToken_isUnauthorized() {
        given().header(ServiceTokenFilter.HEADER, "not-the-token")
                .when().get("/internal/customers/" + SEEDED_CPF)
                .then().statusCode(401);
    }

    // Um JWT de ADMIN nao substitui a credencial de servico: a allowlist do Gateway pode ser contornada com
    // `..;/`, e e esta checagem que fecha a porta.
    @Test
    void findByDocument_withAnAdminTokenButNoServiceToken_isUnauthorized() {
        given().header("Authorization", "Bearer " + TokenUtils.generateAdminToken())
                .when().get("/internal/customers/" + SEEDED_CPF)
                .then().statusCode(401);
    }

    @Test
    void findByDocument_forACpfThatIsNotACustomer_isNotFound() {
        given().header(ServiceTokenFilter.HEADER, TOKEN)
                .when().get("/internal/customers/00000000000")
                .then().statusCode(404);
    }
}
