package br.com.fiap.postech.carworkshop.workorder.infrastructure.metrics;

import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkOrderMetricsAdapterTest {

    private static final String BRANCH = "SP-01";

    private MeterRegistry registry;
    private WorkOrderMetricsAdapter adapter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        adapter = new WorkOrderMetricsAdapter(registry);
    }

    @Test
    void recordStatusChange_countsPerStatus() {
        adapter.recordStatusChange(StatusWO.UNDER_DIAGNOSIS, BRANCH);
        adapter.recordStatusChange(StatusWO.IN_PROGRESS, BRANCH);
        adapter.recordStatusChange(StatusWO.IN_PROGRESS, BRANCH);

        assertEquals(1.0, counterFor(StatusWO.UNDER_DIAGNOSIS, BRANCH));
        assertEquals(2.0, counterFor(StatusWO.IN_PROGRESS, BRANCH));
    }

    @Test
    void recordStatusChange_keepsEachBranchOnItsOwnSeries() {
        adapter.recordStatusChange(StatusWO.COMPLETED, BRANCH);
        adapter.recordStatusChange(StatusWO.COMPLETED, "RJ-01");
        adapter.recordStatusChange(StatusWO.COMPLETED, "RJ-01");

        assertEquals(1.0, counterFor(StatusWO.COMPLETED, BRANCH));
        assertEquals(2.0, counterFor(StatusWO.COMPLETED, "RJ-01"));
    }

    @Test
    void recordStatusChange_keepsEachStatusOnItsOwnSeries() {
        adapter.recordStatusChange(StatusWO.COMPLETED, BRANCH);

        assertEquals(1.0, counterFor(StatusWO.COMPLETED, BRANCH));
        assertNull(registry.find(WorkOrderMetricsAdapter.STATUS_CHANGES)
                .tag("status", StatusWO.DELIVERED.name()).counter());
    }

    @Test
    void recordStatusChange_fallsBackToAnUnknownBranchTag() {
        adapter.recordStatusChange(StatusWO.RECEIVED, null);
        adapter.recordStatusChange(StatusWO.RECEIVED, "  ");

        assertEquals(2.0, counterFor(StatusWO.RECEIVED, WorkOrderMetricsAdapter.UNKNOWN_BRANCH));
    }

    @Test
    void recordStatusChange_ignoresNull() {
        adapter.recordStatusChange(null, BRANCH);

        assertNull(registry.find(WorkOrderMetricsAdapter.STATUS_CHANGES).counter());
    }

    @Test
    void recordCompletion_recordsElapsedTimePerBranch() {
        adapter.recordCompletion(BRANCH, Duration.ofHours(3));

        Timer timer = registry.get(WorkOrderMetricsAdapter.COMPLETION_TIME).tag("branch", BRANCH).timer();
        assertEquals(1L, timer.count());
        assertEquals(3.0, timer.totalTime(TimeUnit.HOURS), 0.0001);
    }

    @Test
    void recordCompletion_ignoresNullAndNegative() {
        adapter.recordCompletion(BRANCH, null);
        adapter.recordCompletion(BRANCH, Duration.ofMinutes(-30));

        assertNull(registry.find(WorkOrderMetricsAdapter.COMPLETION_TIME).timer());
    }

    @Test
    void recordTimeToStatus_keepsOneTimerPerStatus() {
        adapter.recordTimeToStatus(StatusWO.UNDER_DIAGNOSIS, BRANCH, Duration.ofMinutes(20));
        adapter.recordTimeToStatus(StatusWO.COMPLETED, BRANCH, Duration.ofHours(2));
        adapter.recordTimeToStatus(StatusWO.COMPLETED, BRANCH, Duration.ofHours(4));

        assertEquals(1L, timerFor(StatusWO.UNDER_DIAGNOSIS).count());
        assertEquals(2L, timerFor(StatusWO.COMPLETED).count());
        assertEquals(3.0, timerFor(StatusWO.COMPLETED).mean(TimeUnit.HOURS), 0.0001);
    }

    @Test
    void recordTimeToStatus_ignoresNullAndNegative() {
        adapter.recordTimeToStatus(null, BRANCH, Duration.ofMinutes(5));
        adapter.recordTimeToStatus(StatusWO.IN_PROGRESS, BRANCH, null);
        adapter.recordTimeToStatus(StatusWO.IN_PROGRESS, BRANCH, Duration.ofMinutes(-5));

        assertNull(registry.find(WorkOrderMetricsAdapter.TIME_TO_STATUS).timer());
    }

    private Timer timerFor(StatusWO status) {
        return registry.get(WorkOrderMetricsAdapter.TIME_TO_STATUS)
                .tag("status", status.name()).tag("branch", BRANCH).timer();
    }

    private double counterFor(StatusWO status, String branch) {
        return registry.get(WorkOrderMetricsAdapter.STATUS_CHANGES)
                .tag("status", status.name()).tag("branch", branch).counter().count();
    }
}
