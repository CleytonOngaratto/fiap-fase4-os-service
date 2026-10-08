package br.com.fiap.postech.carworkshop.vehicle.usecase.port.out;

public interface CustomerExistencePort {
    boolean existsById(Long customerId);
}
