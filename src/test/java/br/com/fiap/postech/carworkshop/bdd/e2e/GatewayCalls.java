package br.com.fiap.postech.carworkshop.bdd.e2e;

import io.restassured.filter.FilterContext;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

final class GatewayCalls {

    private final String baseUrl;
    private final String correlationId;
    private String lastCall;

    GatewayCalls(String baseUrl, String correlationId) {
        this.baseUrl = baseUrl;
        this.correlationId = correlationId;
    }

    String correlationId() {
        return correlationId;
    }

    RequestSpecification anonymous() {
        return given()
                .baseUri(baseUrl)
                .header("X-Trace-Id", correlationId)
                .contentType(ContentType.JSON)
                .filter(this::printTheCall);
    }

    RequestSpecification bearing(String token) {
        return anonymous().header("Authorization", "Bearer " + token);
    }

    // So metodo, caminho e status: o repositorio e publico, e cabecalho ou corpo levariam JWT, senha e CPF ao log.
    private Response printTheCall(FilterableRequestSpecification request, FilterableResponseSpecification response,
                                  FilterContext context) {
        Response answer = context.next(request, response);
        String call = request.getMethod() + " " + request.getDerivedPath() + " -> " + answer.statusCode();
        if (!call.equals(lastCall)) {
            print(call);
        }
        lastCall = call;
        return answer;
    }

    void print(String line) {
        System.out.println("[" + correlationId + "] " + line);
    }

    // O GitHub Actions so reconhece o comando no inicio da linha: com o prefixo do correlationId antes, nao ha aviso.
    void notice(String line) {
        System.out.println("::notice::[" + correlationId + "] " + line);
    }
}
