package br.com.fiap.postech.carworkshop.branch.infrastructure.mapper;

import br.com.fiap.postech.carworkshop.branch.domain.entity.Branch;
import br.com.fiap.postech.carworkshop.branch.infrastructure.persistence.BranchJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta")
public interface BranchJpaMapper {
    Branch toDomain(BranchJpaEntity entity);
}
