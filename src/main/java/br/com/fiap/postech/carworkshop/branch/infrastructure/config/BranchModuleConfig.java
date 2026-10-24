package br.com.fiap.postech.carworkshop.branch.infrastructure.config;

import br.com.fiap.postech.carworkshop.branch.usecase.interactor.BranchInteractor;
import br.com.fiap.postech.carworkshop.branch.usecase.port.in.BranchUseCase;
import br.com.fiap.postech.carworkshop.branch.usecase.port.out.BranchRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class BranchModuleConfig {

    @Produces
    @ApplicationScoped
    public BranchUseCase branchUseCase(BranchRepositoryPort repository) {
        return new BranchInteractor(repository);
    }
}
