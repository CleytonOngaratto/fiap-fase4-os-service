package br.com.fiap.postech.carworkshop.auth.adapter.controller;

import br.com.fiap.postech.carworkshop.auth.adapter.dto.LoginRequest;
import br.com.fiap.postech.carworkshop.auth.adapter.dto.SignupRequest;
import br.com.fiap.postech.carworkshop.auth.adapter.presenter.AuthTokenResponse;
import br.com.fiap.postech.carworkshop.auth.domain.entity.User;
import br.com.fiap.postech.carworkshop.auth.domain.entity.UserRoles;
import br.com.fiap.postech.carworkshop.auth.usecase.port.in.AuthUseCase;
import br.com.fiap.postech.carworkshop.auth.usecase.port.in.RegisterAdminCommand;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ForbiddenException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ValidationException;
import br.com.fiap.postech.carworkshop.utils.TokenUtils;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;

@QuarkusTest
class AuthControllerTest {

    @InjectMock
    AuthUseCase useCase;

    private SignupRequest signupRequest;
    private LoginRequest loginRequest;
    private User createdUser;
    private AuthTokenResponse tokenResponse;

    @BeforeEach
    void setUp() {
        signupRequest = new SignupRequest("test@test.com", "password123");
        loginRequest = new LoginRequest("test@test.com", "password123");
        createdUser = User.builder().id(1L).username("test@test.com")
                .roles(List.of(UserRoles.ADMIN)).build();
        tokenResponse = new AuthTokenResponse("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...");
    }

    @Test
    void signup_byAnAdministrator_tellsTheUseCaseTheCallerIsAnAdmin() {
        Mockito.when(useCase.signup(any(RegisterAdminCommand.class))).thenReturn(createdUser);

        given().contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + TokenUtils.generateAdminToken())
                .body(signupRequest)
                .when().post("/auth/signup").then()
                .statusCode(201).body("id", is(1)).body("username", is("test@test.com"))
                .body("roles[0]", is("ADMIN"));

        Mockito.verify(useCase).signup(
                new RegisterAdminCommand("test@test.com", "password123", true));
    }

    @Test
    void signup_anonymously_reachesTheUseCaseAsANonAdmin() {
        Mockito.when(useCase.signup(any(RegisterAdminCommand.class))).thenReturn(createdUser);

        given().contentType(MediaType.APPLICATION_JSON).body(signupRequest)
                .when().post("/auth/signup").then().statusCode(201);

        Mockito.verify(useCase).signup(
                new RegisterAdminCommand("test@test.com", "password123", false));
    }

    @Test
    void signup_refusedByTheUseCase_isForbidden() {
        Mockito.when(useCase.signup(any(RegisterAdminCommand.class)))
                .thenThrow(new ForbiddenException("Only an administrator can create a user."));

        given().contentType(MediaType.APPLICATION_JSON).body(signupRequest)
                .when().post("/auth/signup").then().statusCode(403);
    }

    @Test
    void signup_withACustomerToken_isNotTreatedAsAnAdmin() {
        Mockito.when(useCase.signup(any(RegisterAdminCommand.class)))
                .thenThrow(new ForbiddenException("Only an administrator can create a user."));

        given().contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + TokenUtils.generateCustomerToken())
                .body(signupRequest)
                .when().post("/auth/signup").then().statusCode(403);

        Mockito.verify(useCase).signup(
                new RegisterAdminCommand("test@test.com", "password123", false));
    }

    @Test
    void signup_ignoresRolesSentInTheRequestBody() {
        Mockito.when(useCase.signup(any(RegisterAdminCommand.class))).thenReturn(createdUser);

        given().contentType(MediaType.APPLICATION_JSON)
                .body("{\"username\":\"intruder\",\"password\":\"x\",\"roles\":[\"ADMIN\"]}")
                .when().post("/auth/signup").then().statusCode(201);

        Mockito.verify(useCase).signup(new RegisterAdminCommand("intruder", "x", false));
    }

    @Test
    void signup_nullBody_isRejected() {
        given().contentType(MediaType.APPLICATION_JSON)
                .when().post("/auth/signup").then().statusCode(400);
    }

    @Test
    void signup_withInvalidData_isRejected() {
        Mockito.when(useCase.signup(any(RegisterAdminCommand.class)))
                .thenThrow(new ValidationException("Username must not be blank."));

        given().contentType(MediaType.APPLICATION_JSON).body(signupRequest)
                .when().post("/auth/signup").then().statusCode(400);
    }

    @Test
    void login_returnsTheToken() {
        Mockito.when(useCase.login(any(LoginRequest.class))).thenReturn(tokenResponse);

        given().contentType(MediaType.APPLICATION_JSON).body(loginRequest)
                .when().post("/auth/login").then()
                .statusCode(200).body("token", is("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."));

        Mockito.verify(useCase).login(any(LoginRequest.class));
    }

    @Test
    void login_withInvalidCredentials_isRejected() {
        Mockito.when(useCase.login(any(LoginRequest.class)))
                .thenThrow(new ValidationException("Invalid username or password."));

        given().contentType(MediaType.APPLICATION_JSON).body(loginRequest)
                .when().post("/auth/login").then().statusCode(400);
    }
}
