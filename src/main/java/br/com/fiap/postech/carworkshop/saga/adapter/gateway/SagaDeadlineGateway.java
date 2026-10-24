package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.saga.usecase.port.out.SagaDeadlinePort;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class SagaDeadlineGateway implements SagaDeadlinePort {

    @ConfigProperty(name = "saga.deadline.approval")
    Duration approval;

    @ConfigProperty(name = "saga.deadline.payment")
    Duration payment;

    @Override
    public Instant approvalDeadline() {
        return Instant.now().plus(approval);
    }

    @Override
    public Instant paymentDeadline() {
        return Instant.now().plus(payment);
    }
}
