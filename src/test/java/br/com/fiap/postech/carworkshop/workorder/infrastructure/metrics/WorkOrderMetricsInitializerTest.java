package br.com.fiap.postech.carworkshop.workorder.infrastructure.metrics;

import br.com.fiap.postech.carworkshop.branch.domain.entity.Branch;
import br.com.fiap.postech.carworkshop.branch.usecase.port.out.BranchRepositoryPort;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkOrderMetricsInitializerTest {

    @Test
    void registersTheSeriesOfEveryBranchAtStartup() {
        MeterRegistry registry = new SimpleMeterRegistry();
        WorkOrderMetricsInitializer initializer = initializer(registry);
        when(initializer.branches.ids()).thenReturn(List.of("SP-01", "RJ-01"));

        initializer.registerSeries(null);

        assertEquals(0.0, registry.get(WorkOrderMetricsAdapter.STATUS_CHANGES)
                .tag("status", StatusWO.DELIVERED.name()).tag("branch", "RJ-01").counter().count());
    }

    @Test
    void keepsBootingWhenTheBranchesCannotBeRead() {
        MeterRegistry registry = new SimpleMeterRegistry();
        WorkOrderMetricsInitializer initializer = initializer(registry);
        when(initializer.branches.ids()).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> initializer.registerSeries(null));

        assertTrue(registry.getMeters().isEmpty());
    }

    @Test
    void readsTheIdOfEveryBranch() {
        BranchRepositoryPort repository = mock(BranchRepositoryPort.class);
        when(repository.findAll()).thenReturn(List.of(
                Branch.builder().id("SP-01").build(), Branch.builder().id("RJ-01").build()));
        WorkOrderMetricsInitializer.BranchReader reader = new WorkOrderMetricsInitializer.BranchReader();
        reader.repository = repository;

        assertEquals(List.of("SP-01", "RJ-01"), reader.ids());
    }

    private static WorkOrderMetricsInitializer initializer(MeterRegistry registry) {
        WorkOrderMetricsInitializer initializer = new WorkOrderMetricsInitializer();
        initializer.branches = mock(WorkOrderMetricsInitializer.BranchReader.class);
        initializer.metrics = new WorkOrderMetricsAdapter(registry);
        return initializer;
    }
}
