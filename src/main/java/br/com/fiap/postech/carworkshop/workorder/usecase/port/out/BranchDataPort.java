package br.com.fiap.postech.carworkshop.workorder.usecase.port.out;

public interface BranchDataPort {
    boolean exists(String branchId);
}
