package br.com.fiap.postech.carworkshop.saga.usecase.port.out;

import java.time.Instant;

public interface SagaDeadlinePort {

    Instant approvalDeadline();

    Instant paymentDeadline();
}
