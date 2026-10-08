package br.com.fiap.postech.carworkshop.inventory.infrastructure.config;

import br.com.fiap.postech.carworkshop.inventory.usecase.interactor.InventoryInteractor;
import br.com.fiap.postech.carworkshop.inventory.usecase.port.in.InventoryUseCase;
import br.com.fiap.postech.carworkshop.inventory.usecase.port.out.InventoryRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class InventoryModuleConfig {

    @Produces
    @ApplicationScoped
    public InventoryUseCase inventoryUseCase(InventoryRepositoryPort repository) {
        return new InventoryInteractor(repository);
    }
}
