package br.com.fiap.postech.carworkshop.workorder.adapter.controller;

import br.com.fiap.postech.carworkshop.shared.domain.exception.ForbiddenException;
import br.com.fiap.postech.carworkshop.workorder.adapter.presenter.WorkOrderTrackingResponse;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderUseCase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/tracking")
@Tag(name = "Customer Tracking")
@RolesAllowed({"CUSTOMER", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkOrderTrackingController {

    static final String ADMIN = "ADMIN";
    static final String CPF_CLAIM = "cpf";

    @Inject
    WorkOrderUseCase useCase;

    @Inject
    JsonWebToken token;

    @GET
    @Path("/{id}")
    @Operation(summary = "Get work order status, details and status history")
    public Response getStatus(@Parameter(description = "Work order ID") @PathParam("id") Long id,
                              @Context SecurityContext security) {
        WorkOrder workOrder = security.isUserInRole(ADMIN)
                ? useCase.findForCustomer(id)
                : useCase.findForOwner(id, requesterDocument());
        return Response.ok(WorkOrderTrackingResponse.from(workOrder, useCase.historyOf(id))).build();
    }

    private String requesterDocument() {
        String cpf = token.getClaim(CPF_CLAIM);
        if (cpf == null || cpf.isBlank()) {
            throw new ForbiddenException("The token carries no cpf claim.");
        }
        return cpf;
    }
}
