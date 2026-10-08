package br.com.fiap.postech.carworkshop.vehicle.adapter.gateway;

import br.com.fiap.postech.carworkshop.vehicle.domain.entity.Vehicle;
import br.com.fiap.postech.carworkshop.vehicle.infrastructure.mapper.VehicleJpaMapper;
import br.com.fiap.postech.carworkshop.vehicle.infrastructure.persistence.VehicleJpaEntity;
import br.com.fiap.postech.carworkshop.vehicle.infrastructure.persistence.VehiclePanacheRepository;
import br.com.fiap.postech.carworkshop.vehicle.usecase.port.out.VehicleRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class VehicleRepositoryGateway implements VehicleRepositoryPort {

    @Inject
    VehiclePanacheRepository repository;

    @Inject
    VehicleJpaMapper mapper;

    @Override
    public List<Vehicle> findAll() {
        return repository.findAll().list().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Vehicle> findById(Long id) {
        return repository.findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Vehicle> findByVehiclePlate(String vehiclePlate) {
        return repository.findByVehiclePlate(vehiclePlate).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Vehicle save(Vehicle vehicle) {
        if (vehicle.getId() != null) {
            VehicleJpaEntity existing = repository.findById(vehicle.getId());
            existing.setVehiclePlate(vehicle.getVehiclePlate());
            existing.setManufacturer(vehicle.getManufacturer());
            existing.setModelName(vehicle.getModelName());
            existing.setModelYear(vehicle.getModelYear());
            return mapper.toDomain(existing);
        }
        VehicleJpaEntity entity = mapper.toJpaEntity(vehicle);
        repository.persistAndFlush(entity);
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional
    public boolean deleteById(Long id) {
        return repository.deleteById(id);
    }
}
