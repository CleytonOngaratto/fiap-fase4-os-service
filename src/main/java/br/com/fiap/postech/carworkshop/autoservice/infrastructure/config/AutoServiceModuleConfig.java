package br.com.fiap.postech.carworkshop.autoservice.infrastructure.config;

import br.com.fiap.postech.carworkshop.autoservice.usecase.interactor.AutoServiceInteractor;
import br.com.fiap.postech.carworkshop.autoservice.usecase.port.in.AutoServiceUseCase;
import br.com.fiap.postech.carworkshop.autoservice.usecase.port.out.AutoServiceRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class AutoServiceModuleConfig {

    @Produces
    @ApplicationScoped
    public AutoServiceUseCase autoServiceUseCase(AutoServiceRepositoryPort repository) {
        return new AutoServiceInteractor(repository);
    }
}
