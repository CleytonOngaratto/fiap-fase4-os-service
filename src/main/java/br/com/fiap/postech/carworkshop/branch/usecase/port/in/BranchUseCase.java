package br.com.fiap.postech.carworkshop.branch.usecase.port.in;

import br.com.fiap.postech.carworkshop.branch.domain.entity.Branch;

import java.util.List;

public interface BranchUseCase {
    List<Branch> findAll();
}
