package br.com.fiap.postech.carworkshop.workorder.infrastructure.observability;

import br.com.fiap.postech.carworkshop.shared.infrastructure.observability.LogContext;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.CorrelationIdPort;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.MDC;

@ApplicationScoped
public class MdcCorrelationIdGateway implements CorrelationIdPort {

    @Override
    public String current() {
        return MDC.get(LogContext.CORRELATION_ID);
    }
}
