package br.com.fiap.postech.carworkshop.saga.usecase.port.out;

import br.com.fiap.postech.carworkshop.saga.domain.message.SagaEnvelope;

public interface SagaOutboxPort {

    void append(SagaEnvelope envelope);
}
