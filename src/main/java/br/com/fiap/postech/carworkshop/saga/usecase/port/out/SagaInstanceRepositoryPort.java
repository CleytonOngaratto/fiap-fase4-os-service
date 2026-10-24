package br.com.fiap.postech.carworkshop.saga.usecase.port.out;

import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaInstance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SagaInstanceRepositoryPort {

    SagaInstance save(SagaInstance instance);

    Optional<SagaInstance> findByWorkOrderId(Long workOrderId);

    List<SagaInstance> claimExpired(Instant now, int limit);
}
