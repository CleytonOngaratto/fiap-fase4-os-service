package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaInboxJpaEntity;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaInboxPanacheRepository;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaInboxPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class SagaInboxGateway implements SagaInboxPort {

    @Inject
    SagaInboxPanacheRepository repository;

    @Override
    // MANDATORY: a marca de "processada" so vale se for gravada na mesma transacao do efeito da mensagem.
    @Transactional(Transactional.TxType.MANDATORY)
    public boolean recordIfNew(UUID messageId, Long workOrderId, String type) {
        if (repository.findById(messageId) != null) {
            return false;
        }
        repository.persist(SagaInboxJpaEntity.builder()
                .messageId(messageId)
                .workOrderId(workOrderId)
                .type(type)
                .processedAt(Instant.now())
                .build());
        return true;
    }
}
