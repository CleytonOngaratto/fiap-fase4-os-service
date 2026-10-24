package br.com.fiap.postech.carworkshop.branch.usecase.port.out;

import br.com.fiap.postech.carworkshop.branch.domain.entity.Branch;

import java.util.List;
import java.util.Optional;

public interface BranchRepositoryPort {
    List<Branch> findAll();
    Optional<Branch> findById(String id);
}
