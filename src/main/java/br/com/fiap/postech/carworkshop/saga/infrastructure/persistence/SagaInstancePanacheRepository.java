package br.com.fiap.postech.carworkshop.saga.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.Session;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SagaInstancePanacheRepository implements PanacheRepositoryBase<SagaInstanceJpaEntity, UUID> {

    private static final String CLAIM_EXPIRED = """
            SELECT * FROM saga_instance
            WHERE deadline_at IS NOT NULL
              AND deadline_at < :now
              AND state IN ('AWAITING_APPROVAL', 'AWAITING_PAYMENT')
            ORDER BY deadline_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """;

    // SKIP LOCKED e o que impede duas replicas de compensarem a mesma saga vencida.
    public List<SagaInstanceJpaEntity> claimExpired(Instant now, int limit) {
        return getEntityManager().unwrap(Session.class)
                .createNativeQuery(CLAIM_EXPIRED, SagaInstanceJpaEntity.class)
                .setParameter("now", now)
                .setParameter("limit", limit)
                .getResultList();
    }

    public SagaInstanceJpaEntity findByWorkOrderId(Long workOrderId) {
        return find("workOrderId", workOrderId).firstResult();
    }

}
