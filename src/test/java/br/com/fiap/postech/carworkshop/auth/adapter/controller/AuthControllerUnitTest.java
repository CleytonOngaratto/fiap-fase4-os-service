package br.com.fiap.postech.carworkshop.auth.adapter.controller;

import br.com.fiap.postech.carworkshop.auth.adapter.dto.LoginRequest;
import br.com.fiap.postech.carworkshop.auth.adapter.presenter.AuthTokenResponse;
import br.com.fiap.postech.carworkshop.auth.usecase.port.in.AuthUseCase;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthControllerUnitTest {

    private AuthUseCase useCase;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(AuthUseCase.class);
        controller = new AuthController();
        controller.useCase = useCase;
    }

    @Test
    void login_withoutABody_isABadRequestThatNeverReachesTheUseCase() {
        assertThrows(BadRequestException.class, () -> controller.login(null));

        verifyNoInteractions(useCase);
    }

    @Test
    void login_withABody_answersTheTokenFromTheUseCase() {
        LoginRequest request = new LoginRequest("admin", "secret");
        AuthTokenResponse token = new AuthTokenResponse("token");
        when(useCase.login(request)).thenReturn(token);

        Response response = controller.login(request);

        assertEquals(200, response.getStatus());
        assertSame(token, response.getEntity());
    }
}
