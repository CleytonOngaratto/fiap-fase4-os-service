package br.com.fiap.postech.carworkshop.saga.infrastructure.persistence;

import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaState;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
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

    private static final String COUNT_BY_STATE = """
            SELECT s.state, s.branchId, COUNT(s) FROM SagaInstanceJpaEntity s
            GROUP BY s.state, s.branchId
            """;

    private static final String COUNT_BY_CANCEL_REASON = """
            SELECT s.cancelReason, s.branchId, COUNT(s) FROM SagaInstanceJpaEntity s
            WHERE s.cancelReason IS NOT NULL
            GROUP BY s.cancelReason, s.branchId
            """;

    private static final String COUNT_OVERDUE = """
            SELECT s.state, s.branchId, COUNT(s) FROM SagaInstanceJpaEntity s
            WHERE (s.state IN :waiting AND s.deadlineAt < :deadlineLimit)
               OR (s.state = :compensating AND s.updatedAt < :compensatingLimit)
            GROUP BY s.state, s.branchId
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

    public List<SagaCount> countByState() {
        return counts(getEntityManager().createQuery(COUNT_BY_STATE, Object[].class));
    }

    public List<SagaCount> countByCancelReason() {
        return counts(getEntityManager().createQuery(COUNT_BY_CANCEL_REASON, Object[].class));
    }

    public List<SagaCount> countOverdue(Instant deadlineLimit, Instant compensatingLimit) {
        return counts(getEntityManager().createQuery(COUNT_OVERDUE, Object[].class)
                .setParameter("waiting", List.of(SagaState.AWAITING_APPROVAL, SagaState.AWAITING_PAYMENT))
                .setParameter("deadlineLimit", deadlineLimit)
                .setParameter("compensating", SagaState.COMPENSATING)
                .setParameter("compensatingLimit", compensatingLimit));
    }

    private static List<SagaCount> counts(TypedQuery<Object[]> query) {
        return query.getResultList().stream().map(SagaCount::of).toList();
    }
}
