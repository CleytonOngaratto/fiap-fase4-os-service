package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.shared.domain.exception.DomainException;
import br.com.fiap.postech.carworkshop.saga.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.saga.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaOutboxJpaEntity;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaOutboxPanacheRepository;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaOutboxPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SagaOutboxGateway implements SagaOutboxPort {

    @Inject
    SagaOutboxPanacheRepository repository;

    @Inject
    ObjectMapper objectMapper;

    @Override
    // MANDATORY, nao REQUIRED: a linha do outbox so vale se for gravada na mesma transacao do estado. Sem
    // transacao ativa isto falha alto, em vez de criar uma transacao propria e virar dual write silencioso.
    @Transactional(Transactional.TxType.MANDATORY)
    public void append(SagaEnvelope envelope) {
        SagaOutboxJpaEntity entity = SagaOutboxJpaEntity.builder()
                .messageId(envelope.messageId())
                .workOrderId(envelope.workOrderId())
                .sagaId(envelope.sagaId())
                .branchId(envelope.branchId())
                .destination(SagaMessageType.destinationOf(envelope.type()).name())
                .type(envelope.type())
                .version(envelope.version())
                .occurredAt(envelope.occurredAt())
                .correlationId(envelope.correlationId())
                .causationId(envelope.causationId())
                .payload(serialize(envelope))
                .build();
        repository.persist(entity);
    }

    private String serialize(SagaEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope.payload());
        } catch (JsonProcessingException e) {
            throw new DomainException("Could not serialize the payload of " + envelope.type() + ".");
        }
    }
}
