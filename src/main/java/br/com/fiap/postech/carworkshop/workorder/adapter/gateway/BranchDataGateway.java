package br.com.fiap.postech.carworkshop.workorder.adapter.gateway;

import br.com.fiap.postech.carworkshop.branch.usecase.port.out.BranchRepositoryPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.BranchDataPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BranchDataGateway implements BranchDataPort {

    @Inject
    BranchRepositoryPort branchRepository;

    @Override
    public boolean exists(String branchId) {
        return branchId != null && branchRepository.findById(branchId).isPresent();
    }
}
