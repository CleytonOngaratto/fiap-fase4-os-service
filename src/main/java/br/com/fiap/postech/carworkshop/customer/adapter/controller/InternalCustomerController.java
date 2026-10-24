package br.com.fiap.postech.carworkshop.customer.adapter.controller;

import br.com.fiap.postech.carworkshop.customer.adapter.presenter.InternalCustomerResponse;
import br.com.fiap.postech.carworkshop.customer.usecase.port.out.CustomerRepositoryPort;
import br.com.fiap.postech.carworkshop.shared.domain.exception.EntityNotFoundException;
import br.com.fiap.postech.carworkshop.shared.infrastructure.security.ServiceAuthenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

// Recurso proprio, fora de /customers: a allowlist do Gateway publica "ANY /carworkshop/v1/customers/{proxy+}",
// e um metodo dentro do CustomerController ficaria exposto na internet.
@Path("/internal/customers")
@Tag(name = "Internal")
@Produces(MediaType.APPLICATION_JSON)
public class InternalCustomerController {

    @Inject
    CustomerRepositoryPort customers;

    @GET
    @PermitAll
    @ServiceAuthenticated
    @Path("/{cpf}")
    @Operation(summary = "Confirm a CPF belongs to a customer (service to service, outside the public edge)")
    public Response findByDocument(@PathParam("cpf") String cpf) {
        return customers.findByDocument(cpf)
                .map(customer -> Response.ok(InternalCustomerResponse.from(customer)).build())
                .orElseThrow(() -> new EntityNotFoundException("No customer with this CPF."));
    }
}
