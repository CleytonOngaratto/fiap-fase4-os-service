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

@Entity(name = "SagaInboxJpaEntity")
@Table(name = "saga_inbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaInboxJpaEntity extends PanacheEntityBase {

    @Id
    @Column(name = "message_id")
    public UUID messageId;

    @Column(name = "work_order_id")
    private Long workOrderId;

    private String type;

    @Column(name = "processed_at")
    private Instant processedAt;
}
