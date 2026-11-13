package br.com.fiap.postech.carworkshop.autoservice.adapter.controller;

import br.com.fiap.postech.carworkshop.autoservice.usecase.port.in.AutoServiceUseCase;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AutoServiceControllerUnitTest {

    private AutoServiceUseCase useCase;
    private AutoServiceController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(AutoServiceUseCase.class);
        controller = new AutoServiceController();
        controller.useCase = useCase;
    }

    @Test
    void create_withoutABody_isABadRequestThatNeverReachesTheUseCase() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> controller.create(null));

        assertEquals("Request body is required.", error.getMessage());
        verifyNoInteractions(useCase);
    }

    @Test
    void update_withoutABody_isABadRequestThatNeverReachesTheUseCase() {
        assertThrows(BadRequestException.class, () -> controller.update(1L, null));

        verifyNoInteractions(useCase);
    }
}
