package br.com.fiap.postech.carworkshop.branch.usecase.interactor;

import br.com.fiap.postech.carworkshop.branch.domain.entity.Branch;
import br.com.fiap.postech.carworkshop.branch.usecase.port.in.BranchUseCase;
import br.com.fiap.postech.carworkshop.branch.usecase.port.out.BranchRepositoryPort;

import java.util.List;

public class BranchInteractor implements BranchUseCase {

    private final BranchRepositoryPort repository;

    public BranchInteractor(BranchRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<Branch> findAll() {
        return repository.findAll();
    }
}
