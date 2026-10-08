package br.com.fiap.postech.carworkshop.vehicle.usecase.interactor;

import br.com.fiap.postech.carworkshop.shared.domain.exception.EntityNotFoundException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ValidationException;
import br.com.fiap.postech.carworkshop.vehicle.adapter.dto.VehicleRequest;
import br.com.fiap.postech.carworkshop.vehicle.usecase.interactor.VehicleInteractor;
import br.com.fiap.postech.carworkshop.vehicle.usecase.port.out.CustomerExistencePort;
import br.com.fiap.postech.carworkshop.vehicle.usecase.port.out.VehicleRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VehicleOrphanPreventionD3Test {

    @InjectMocks
    VehicleInteractor interactor;
    @Mock
    VehicleRepositoryPort repository;
    @Mock
    CustomerExistencePort customerExistencePort;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void create_withNullCustomerId_isRejected_andNothingPersisted() {
        VehicleRequest noOwner = new VehicleRequest("ABC-1234", "Toyota", "Corolla", 2020, null);
        when(repository.findByVehiclePlate(any())).thenReturn(Optional.empty());

        assertThrows(ValidationException.class, () -> interactor.create(noOwner));

        verify(customerExistencePort, never()).existsById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void create_withMissingCustomer_isRejected_andNothingPersisted() {
        VehicleRequest withMissingOwner = new VehicleRequest("ABC-1234", "Toyota", "Corolla", 2020, 999L);
        when(repository.findByVehiclePlate(any())).thenReturn(Optional.empty());
        when(customerExistencePort.existsById(999L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> interactor.create(withMissingOwner));

        verify(repository, never()).save(any());
    }
}
