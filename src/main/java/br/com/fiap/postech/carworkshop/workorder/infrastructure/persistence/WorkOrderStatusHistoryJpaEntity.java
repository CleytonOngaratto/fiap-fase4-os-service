package br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence;

import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "WorkOrderStatusHistoryJpaEntity")
@Table(name = "work_order_status_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderStatusHistoryJpaEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "work_order_status_history_seq")
    @SequenceGenerator(name = "work_order_status_history_seq",
            sequenceName = "work_order_status_history_seq", allocationSize = 1)
    public Long id;

    @Column(name = "work_order_id")
    private Long workOrderId;

    @Column(name = "branch_id")
    private String branchId;

    @Enumerated(EnumType.STRING)
    private StatusWO status;

    @Enumerated(EnumType.STRING)
    private CancelReason reason;

    @Column(name = "saga_id")
    private UUID sagaId;

    @Column(name = "changed_at")
    private Instant changedAt;
}
