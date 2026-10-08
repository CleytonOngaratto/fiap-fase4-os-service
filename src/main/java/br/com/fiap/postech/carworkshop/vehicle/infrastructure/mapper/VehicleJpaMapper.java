package br.com.fiap.postech.carworkshop.vehicle.infrastructure.mapper;

import br.com.fiap.postech.carworkshop.vehicle.domain.entity.Vehicle;
import br.com.fiap.postech.carworkshop.vehicle.infrastructure.persistence.VehicleJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta")
public interface VehicleJpaMapper {

    Vehicle toDomain(VehicleJpaEntity entity);

    VehicleJpaEntity toJpaEntity(Vehicle domain);
}
