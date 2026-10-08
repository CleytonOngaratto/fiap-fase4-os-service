package br.com.fiap.postech.carworkshop.vehicle.adapter.gateway;

import br.com.fiap.postech.carworkshop.customer.usecase.port.out.CustomerRepositoryPort;
import br.com.fiap.postech.carworkshop.vehicle.usecase.port.out.CustomerExistencePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CustomerExistenceGateway implements CustomerExistencePort {

    @Inject
    CustomerRepositoryPort customerRepository;

    @Override
    public boolean existsById(Long customerId) {
        return customerRepository.findById(customerId).isPresent();
    }
}
