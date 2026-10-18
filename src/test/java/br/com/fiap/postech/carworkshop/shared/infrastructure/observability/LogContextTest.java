package br.com.fiap.postech.carworkshop.shared.infrastructure.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void of_putsBothKeysAndClosingRemovesThem() {
        UUID sagaId = UUID.randomUUID();

        try (LogContext ignored = LogContext.of("EVIDENCIA-B3-001", sagaId)) {
            assertEquals("EVIDENCIA-B3-001", MDC.get(LogContext.CORRELATION_ID));
            assertEquals(sagaId.toString(), MDC.get(LogContext.SAGA_ID));
        }

        assertNull(MDC.get(LogContext.CORRELATION_ID));
        assertNull(MDC.get(LogContext.SAGA_ID));
    }

    @Test
    void of_skipsValuesThatAreAbsentOrBlank() {
        try (LogContext ignored = LogContext.of("  ", null)) {
            assertNull(MDC.get(LogContext.CORRELATION_ID));
            assertNull(MDC.get(LogContext.SAGA_ID));
        }
    }
}
