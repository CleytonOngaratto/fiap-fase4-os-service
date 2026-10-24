package br.com.fiap.postech.carworkshop.saga.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "SagaOutboxJpaEntity")
@Table(name = "saga_outbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaOutboxJpaEntity extends PanacheEntityBase {

    @Id
    @Column(name = "message_id")
    public UUID messageId;

    @Column(name = "work_order_id")
    private Long workOrderId;

    @Column(name = "saga_id")
    private UUID sagaId;

    @Column(name = "branch_id")
    private String branchId;

    private String destination;

    private String type;

    private int version;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "causation_id")
    private UUID causationId;

    private String payload;

    @Column(name = "sent_at")
    private Instant sentAt;
}
