package br.com.fiap.postech.carworkshop.branch.adapter.presenter;

import br.com.fiap.postech.carworkshop.branch.domain.entity.Branch;

public record BranchResponse(String id, String name, String city) {

    public static BranchResponse from(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getName(), branch.getCity());
    }
}
