package br.com.fiap.postech.carworkshop.branch.usecase.interactor;

import br.com.fiap.postech.carworkshop.branch.adapter.presenter.BranchResponse;
import br.com.fiap.postech.carworkshop.branch.domain.entity.Branch;
import br.com.fiap.postech.carworkshop.branch.usecase.port.out.BranchRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BranchInteractorTest {

    private final BranchRepositoryPort repository = mock(BranchRepositoryPort.class);
    private final BranchInteractor interactor = new BranchInteractor(repository);

    @Test
    void findAll_returnsTheSeededBranches() {
        when(repository.findAll()).thenReturn(List.of(
                Branch.builder().id("SP-01").name("Unidade Paulista").city("Sao Paulo").build(),
                Branch.builder().id("RJ-01").name("Unidade Barra").city("Rio de Janeiro").build()));

        List<Branch> branches = interactor.findAll();

        assertEquals(List.of("SP-01", "RJ-01"), branches.stream().map(Branch::getId).toList());
    }

    @Test
    void findAll_withoutBranches_isEmpty() {
        when(repository.findAll()).thenReturn(List.of());

        assertTrue(interactor.findAll().isEmpty());
    }

    @Test
    void branchResponse_mapsTheDomain() {
        Branch branch = Branch.builder().id("SP-02").name("Unidade Santo Amaro").city("Sao Paulo").build();

        BranchResponse response = BranchResponse.from(branch);

        assertEquals("SP-02", response.id());
        assertEquals("Unidade Santo Amaro", response.name());
        assertEquals("Sao Paulo", response.city());
    }
}
