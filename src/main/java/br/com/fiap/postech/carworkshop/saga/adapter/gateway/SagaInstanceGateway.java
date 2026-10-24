package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.saga.domain.entity.CompensationStep;
import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaInstance;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaInstanceJpaEntity;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaInstancePanacheRepository;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaInstanceRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SagaInstanceGateway implements SagaInstanceRepositoryPort {

    @Inject
    SagaInstancePanacheRepository repository;

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public SagaInstance save(SagaInstance instance) {
        SagaInstanceJpaEntity entity = repository.findById(instance.getSagaId());
        if (entity == null) {
            entity = toEntity(instance);
            repository.persist(entity);
        } else {
            copyInto(instance, entity);
        }
        instance.setVersion(entity.getVersion());
        return instance;
    }

    @Override
    public Optional<SagaInstance> findByWorkOrderId(Long workOrderId) {
        return Optional.ofNullable(repository.findByWorkOrderId(workOrderId)).map(SagaInstanceGateway::toDomain);
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public List<SagaInstance> claimExpired(Instant now, int limit) {
        return repository.claimExpired(now, limit).stream().map(SagaInstanceGateway::toDomain).toList();
    }

    private static SagaInstanceJpaEntity toEntity(SagaInstance instance) {
        return SagaInstanceJpaEntity.builder()
                .sagaId(instance.getSagaId())
                .workOrderId(instance.getWorkOrderId())
                .branchId(instance.getBranchId())
                .state(instance.getState())
                .cancelReason(instance.getCancelReason())
                .budgetId(instance.getBudgetId())
                .paymentId(instance.getPaymentId())
                .pendingCompensations(encode(instance.getPendingCompensations()))
                .deadlineAt(instance.getDeadlineAt())
                .createdAt(instance.getCreatedAt())
                .updatedAt(instance.getUpdatedAt())
                .build();
    }

    private static void copyInto(SagaInstance instance, SagaInstanceJpaEntity entity) {
        entity.setState(instance.getState());
        entity.setCancelReason(instance.getCancelReason());
        entity.setBudgetId(instance.getBudgetId());
        entity.setPaymentId(instance.getPaymentId());
        entity.setPendingCompensations(encode(instance.getPendingCompensations()));
        entity.setDeadlineAt(instance.getDeadlineAt());
        entity.setUpdatedAt(instance.getUpdatedAt());
    }

    private static SagaInstance toDomain(SagaInstanceJpaEntity entity) {
        return SagaInstance.builder()
                .sagaId(entity.sagaId)
                .workOrderId(entity.getWorkOrderId())
                .branchId(entity.getBranchId())
                .state(entity.getState())
                .cancelReason(entity.getCancelReason())
                .budgetId(entity.getBudgetId())
                .paymentId(entity.getPaymentId())
                .pendingCompensations(decode(entity.getPendingCompensations()))
                .deadlineAt(entity.getDeadlineAt())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static String encode(Set<CompensationStep> steps) {
        return steps == null || steps.isEmpty()
                ? null
                : steps.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static Set<CompensationStep> decode(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(value.split(","))
                .map(CompensationStep::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
