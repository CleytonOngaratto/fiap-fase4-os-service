package br.com.fiap.postech.carworkshop.workorder.adapter.gateway;

import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.mapper.WorkOrderJpaMapper;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.WorkOrderJpaEntity;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.WorkOrderPanacheRepository;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class WorkOrderRepositoryGateway implements WorkOrderRepositoryPort {

    @Inject
    WorkOrderPanacheRepository repository;

    @Inject
    WorkOrderJpaMapper mapper;

    @Override
    @Transactional
    public WorkOrder save(WorkOrder workOrder) {
        if (workOrder.getId() != null) {
            WorkOrderJpaEntity existing = repository.findById(workOrder.getId());
            existing.setStatus(workOrder.getStatus());
            existing.setDiagnosticDescription(workOrder.getDiagnosticDescription());
            existing.setBudgetValue(workOrder.getBudgetValue());
            existing.setBudgetApprovalDate(workOrder.getBudgetApprovalDate());
            existing.setEndDate(workOrder.getEndDate());
            existing.setDeleted(workOrder.isDeleted());
            // Os itens chegam no diagnostico, depois da criacao: sem copiar as colecoes aqui, o total
            // persiste e o snapshot precificado nao, e a reserva de estoque nao encontra peca nenhuma.
            if (workOrder.getServices() != null) {
                existing.setServices(mapper.toServiceSnapshots(workOrder.getServices()));
            }
            if (workOrder.getParts() != null) {
                existing.setParts(mapper.toPartSnapshots(workOrder.getParts()));
            }
            return mapper.toDomain(existing);
        }
        WorkOrderJpaEntity entity = mapper.toJpaEntity(workOrder);
        repository.persistAndFlush(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<WorkOrder> findById(Long id) {
        return repository.findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public List<WorkOrder> findAll() {
        return repository.findAll().list().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<WorkOrder> findAllActive() {
        return repository.findAllActive().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<WorkOrder> findByStatus(StatusWO status) {
        return repository.findByStatus(status).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<WorkOrder> findCompleted() {
        return repository.findCompleted().stream().map(mapper::toDomain).toList();
    }
}
