package br.com.fiap.postech.carworkshop.saga.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.Session;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SagaOutboxPanacheRepository implements PanacheRepositoryBase<SagaOutboxJpaEntity, UUID> {

    private static final String CLAIM_PENDING = """
            SELECT * FROM saga_outbox
            WHERE sent_at IS NULL
              AND destination IN (:destinations)
            ORDER BY occurred_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """;

    // Filtrar por destino configurado e o que impede uma linha sem fila de ocupar as vagas do LIMIT e travar
    // as mensagens de tras (head-of-line); SKIP LOCKED permite varias replicas drenarem sem duplicar.
    public List<SagaOutboxJpaEntity> claimPending(Collection<String> destinations, int limit) {
        if (destinations.isEmpty()) {
            return List.of();
        }
        return getEntityManager().unwrap(Session.class)
                .createNativeQuery(CLAIM_PENDING, SagaOutboxJpaEntity.class)
                .setParameterList("destinations", destinations)
                .setParameter("limit", limit)
                .getResultList();
    }
}
