package br.com.fiap.postech.carworkshop.auth.adapter.controller;

import br.com.fiap.postech.carworkshop.auth.adapter.dto.LoginRequest;
import br.com.fiap.postech.carworkshop.auth.adapter.dto.SignupRequest;
import br.com.fiap.postech.carworkshop.auth.adapter.presenter.AuthTokenResponse;
import br.com.fiap.postech.carworkshop.auth.adapter.presenter.UserResponse;
import br.com.fiap.postech.carworkshop.auth.usecase.port.in.AuthUseCase;
import br.com.fiap.postech.carworkshop.auth.usecase.port.in.RegisterAdminCommand;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/auth")
@Tag(name = "Authentication")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthController {

    static final String ADMIN = "ADMIN";

    @Inject
    AuthUseCase useCase;

    // @PermitAll e nao @RolesAllowed porque o primeiro administrador precisa nascer sem ninguem para
    // autoriza-lo; quem decide e o caso de uso, que recusa a chamada anonima depois do primeiro usuario.
    @POST
    @PermitAll
    @Path("/signup")
    @Operation(summary = "Register an administrator (requires an administrator, except for the first one)")
    public Response signup(SignupRequest request, @Context SecurityContext security) {
        if (request == null) throw new BadRequestException("Request body is required.");
        UserResponse created = UserResponse.from(useCase.signup(new RegisterAdminCommand(
                request.username(), request.password(), security.isUserInRole(ADMIN))));
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @POST
    @PermitAll
    @Path("/login")
    @Operation(summary = "Authenticate and obtain a JWT token")
    public Response login(LoginRequest request) {
        AuthTokenResponse token = useCase.login(request);
        return Response.ok(token).build();
    }
}
