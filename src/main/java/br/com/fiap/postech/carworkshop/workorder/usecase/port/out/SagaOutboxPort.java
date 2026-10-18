package br.com.fiap.postech.carworkshop.workorder.usecase.port.out;

import br.com.fiap.postech.carworkshop.workorder.domain.message.SagaEnvelope;

public interface SagaOutboxPort {

    void append(SagaEnvelope envelope);
}
