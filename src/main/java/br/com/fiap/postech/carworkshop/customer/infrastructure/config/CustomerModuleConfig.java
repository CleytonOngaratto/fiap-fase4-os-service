package br.com.fiap.postech.carworkshop.customer.infrastructure.config;

import br.com.fiap.postech.carworkshop.customer.usecase.interactor.CustomerInteractor;
import br.com.fiap.postech.carworkshop.customer.usecase.port.in.CustomerUseCase;
import br.com.fiap.postech.carworkshop.customer.usecase.port.out.CustomerRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class CustomerModuleConfig {

    @Produces
    @ApplicationScoped
    public CustomerUseCase customerUseCase(CustomerRepositoryPort repository) {
        return new CustomerInteractor(repository);
    }
}
