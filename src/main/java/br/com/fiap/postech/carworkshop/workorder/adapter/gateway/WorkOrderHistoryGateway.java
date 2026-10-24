package br.com.fiap.postech.carworkshop.workorder.adapter.gateway;

import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrderStatusChange;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.WorkOrderStatusHistoryJpaEntity;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.WorkOrderStatusHistoryPanacheRepository;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderHistoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class WorkOrderHistoryGateway implements WorkOrderHistoryPort {

    @Inject
    WorkOrderStatusHistoryPanacheRepository repository;

    @Override
    // MANDATORY: a linha do historico so vale na mesma transacao da transicao que ela registra.
    @Transactional(Transactional.TxType.MANDATORY)
    public void record(WorkOrderStatusChange change) {
        repository.persist(WorkOrderStatusHistoryJpaEntity.builder()
                .workOrderId(change.workOrderId())
                .branchId(change.branchId())
                .status(change.status())
                .reason(change.reason())
                .sagaId(change.sagaId())
                .changedAt(change.changedAt())
                .build());
    }

    @Override
    public List<WorkOrderStatusChange> findByWorkOrderId(Long workOrderId) {
        return repository.findByWorkOrderId(workOrderId).stream()
                .map(row -> new WorkOrderStatusChange(row.getWorkOrderId(), row.getBranchId(),
                        row.getStatus(), row.getReason(), row.getSagaId(), row.getChangedAt()))
                .toList();
    }
}
