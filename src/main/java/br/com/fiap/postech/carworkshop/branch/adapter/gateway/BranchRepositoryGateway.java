package br.com.fiap.postech.carworkshop.branch.adapter.gateway;

import br.com.fiap.postech.carworkshop.branch.domain.entity.Branch;
import br.com.fiap.postech.carworkshop.branch.infrastructure.mapper.BranchJpaMapper;
import br.com.fiap.postech.carworkshop.branch.infrastructure.persistence.BranchPanacheRepository;
import br.com.fiap.postech.carworkshop.branch.usecase.port.out.BranchRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class BranchRepositoryGateway implements BranchRepositoryPort {

    @Inject
    BranchPanacheRepository repository;

    @Inject
    BranchJpaMapper mapper;

    @Override
    public List<Branch> findAll() {
        return repository.findAll().list().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Branch> findById(String id) {
        return repository.findByIdOptional(id).map(mapper::toDomain);
    }
}
