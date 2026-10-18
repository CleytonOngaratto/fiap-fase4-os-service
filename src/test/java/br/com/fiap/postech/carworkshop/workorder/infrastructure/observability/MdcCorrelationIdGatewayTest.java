package br.com.fiap.postech.carworkshop.workorder.infrastructure.observability;

import br.com.fiap.postech.carworkshop.shared.infrastructure.observability.LogContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MdcCorrelationIdGatewayTest {

    private final MdcCorrelationIdGateway gateway = new MdcCorrelationIdGateway();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void current_readsWhatTheRequestFilterPutInTheMdc() {
        MDC.put(LogContext.CORRELATION_ID, "EVIDENCIA-B3-001");

        assertEquals("EVIDENCIA-B3-001", gateway.current());
    }

    @Test
    void current_isNullOutsideOfARequest() {
        assertNull(gateway.current());
    }
}
