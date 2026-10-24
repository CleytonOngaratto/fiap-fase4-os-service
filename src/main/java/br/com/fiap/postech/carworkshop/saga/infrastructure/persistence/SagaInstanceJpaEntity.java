package br.com.fiap.postech.carworkshop.saga.infrastructure.persistence;

import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaState;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "SagaInstanceJpaEntity")
@Table(name = "saga_instance")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaInstanceJpaEntity extends PanacheEntityBase {

    @Id
    @Column(name = "saga_id")
    public UUID sagaId;

    @Column(name = "work_order_id")
    private Long workOrderId;

    @Column(name = "branch_id")
    private String branchId;

    @Enumerated(EnumType.STRING)
    private SagaState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason")
    private CancelReason cancelReason;

    @Column(name = "budget_id")
    private String budgetId;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "pending_compensations")
    private String pendingCompensations;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    // Versao otimista: o scheduler de prazos e o consumidor de respostas podem mexer na mesma saga ao mesmo
    // tempo, e so um vence; o outro rele o estado e decide de novo.
    @Version
    private Long version;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
