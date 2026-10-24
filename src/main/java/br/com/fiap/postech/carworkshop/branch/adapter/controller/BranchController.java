package br.com.fiap.postech.carworkshop.branch.adapter.controller;

import br.com.fiap.postech.carworkshop.branch.adapter.presenter.BranchResponse;
import br.com.fiap.postech.carworkshop.branch.usecase.port.in.BranchUseCase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/branches")
@Tag(name = "Branches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BranchController {

    @Inject
    BranchUseCase useCase;

    @GET
    @RolesAllowed("ADMIN")
    @Operation(summary = "List the branches a work order can be opened at")
    public Response findAll() {
        List<BranchResponse> branches = useCase.findAll().stream().map(BranchResponse::from).toList();
        return Response.ok(branches).build();
    }
}
