package br.com.fiap.postech.carworkshop.saga.infrastructure.metrics;

import br.com.fiap.postech.carworkshop.branch.domain.entity.Branch;
import br.com.fiap.postech.carworkshop.branch.usecase.port.out.BranchRepositoryPort;
import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaState;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaCount;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaInstancePanacheRepository;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaMetricsCollectorTest {

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    private SagaInstancePanacheRepository repository;
    private BranchRepositoryPort branches;
    private MeterRegistry registry;
    private SagaMetricsCollector collector;

    @BeforeEach
    void setUp() {
        repository = mock(SagaInstancePanacheRepository.class);
        branches = mock(BranchRepositoryPort.class);
        registry = new SimpleMeterRegistry();
        collector = new SagaMetricsCollector();
        collector.repository = repository;
        collector.branches = branches;
        collector.registry = registry;
        collector.clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(branches.findAll()).thenReturn(List.of(branch("SP-01"), branch("RJ-01")));
    }

    @Test
    void reportsZeroForEveryStateReasonAndBranchWithoutSagas() {
        collector.collect();

        assertEquals(0.0, gauge(SagaMetricsCollector.INSTANCES, "state", "COMPLETED", "RJ-01"));
        assertEquals(0.0, gauge(SagaMetricsCollector.COMPENSATIONS, "reason", "PAYMENT_EXPIRED", "SP-01"));
        assertEquals(0.0, overdue("RJ-01"));
        assertEquals(2 * (SagaState.values().length + CancelReason.values().length + 1),
                registry.getMeters().size());
    }

    @Test
    void reportsWhatTheSagasCount() {
        when(repository.countByState()).thenReturn(List.of(new SagaCount("AWAITING_PAYMENT", "SP-01", 3)));
        when(repository.countByCancelReason()).thenReturn(List.of(new SagaCount("PAYMENT_EXPIRED", "SP-01", 2)));
        when(repository.countOverdue(any(), any())).thenReturn(List.of(
                new SagaCount("AWAITING_PAYMENT", "SP-01", 1),
                new SagaCount("COMPENSATING", "SP-01", 1)));

        collector.collect();

        assertEquals(3.0, gauge(SagaMetricsCollector.INSTANCES, "state", "AWAITING_PAYMENT", "SP-01"));
        assertEquals(0.0, gauge(SagaMetricsCollector.INSTANCES, "state", "AWAITING_PAYMENT", "RJ-01"));
        assertEquals(2.0, gauge(SagaMetricsCollector.COMPENSATIONS, "reason", "PAYMENT_EXPIRED", "SP-01"));
        assertEquals(2.0, overdue("SP-01"));
        assertEquals(0.0, overdue("RJ-01"));
    }

    @Test
    void looksForOverdueSagasPastTheGraceAndTheCompensatingLimit() {
        collector.collect();

        verify(repository).countOverdue(NOW.minus(Duration.ofMinutes(2)), NOW.minus(Duration.ofMinutes(15)));
    }

    @Test
    void bringsAStateBackToZeroWhenItsSagasMoveOn() {
        when(repository.countByState()).thenReturn(List.of(new SagaCount("AWAITING_PAYMENT", "SP-01", 3)));
        collector.collect();
        when(repository.countByState()).thenReturn(List.of());

        collector.collect();

        assertEquals(0.0, gauge(SagaMetricsCollector.INSTANCES, "state", "AWAITING_PAYMENT", "SP-01"));
    }

    @Test
    void reportsABranchThatOnlyTheSagasKnow() {
        when(repository.countByState()).thenReturn(List.of(
                new SagaCount("COMPLETED", SagaCount.UNKNOWN_BRANCH, 4)));

        collector.collect();

        assertEquals(4.0, gauge(SagaMetricsCollector.INSTANCES, "state", "COMPLETED", SagaCount.UNKNOWN_BRANCH));
        assertEquals(0.0, overdue(SagaCount.UNKNOWN_BRANCH));
    }

    @Test
    void reportsUnknownWhenTheSagasCannotBeRead() {
        when(repository.countByState()).thenReturn(List.of(new SagaCount("AWAITING_PAYMENT", "SP-01", 3)));
        collector.collect();
        when(repository.countByState()).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> collector.collect());

        assertTrue(Double.isNaN(gauge(SagaMetricsCollector.INSTANCES, "state", "AWAITING_PAYMENT", "SP-01")));
        assertTrue(Double.isNaN(overdue("RJ-01")));
    }

    @Test
    void startsWithoutSeriesWhenTheFirstReadFails() {
        when(branches.findAll()).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> collector.collect());

        assertTrue(registry.getMeters().isEmpty());
    }

    private double gauge(String name, String key, String value, String branch) {
        return registry.get(name).tag(key, value).tag("branch", branch).gauge().value();
    }

    private double overdue(String branch) {
        return registry.get(SagaMetricsCollector.OVERDUE).tag("branch", branch).gauge().value();
    }

    private static Branch branch(String id) {
        return Branch.builder().id(id).build();
    }
}
