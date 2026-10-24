package br.com.fiap.postech.carworkshop.branch.infrastructure.config;

import br.com.fiap.postech.carworkshop.branch.usecase.interactor.BranchInteractor;
import br.com.fiap.postech.carworkshop.branch.usecase.port.in.BranchUseCase;
import br.com.fiap.postech.carworkshop.branch.usecase.port.out.BranchRepositoryPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class BranchModuleConfigTest {

    @Test
    void branchUseCase_buildsWiredInteractor() {
        BranchUseCase useCase = new BranchModuleConfig().branchUseCase(mock(BranchRepositoryPort.class));

        assertNotNull(useCase);
        assertInstanceOf(BranchInteractor.class, useCase);
    }
}
