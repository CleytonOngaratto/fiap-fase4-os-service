package br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class WorkOrderStatusHistoryPanacheRepository
        implements PanacheRepository<WorkOrderStatusHistoryJpaEntity> {

    public List<WorkOrderStatusHistoryJpaEntity> findByWorkOrderId(Long workOrderId) {
        return list("work_order_id = ?1 order by changed_at", workOrderId);
    }
}
