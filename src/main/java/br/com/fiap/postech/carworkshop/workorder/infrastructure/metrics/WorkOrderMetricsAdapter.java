package br.com.fiap.postech.carworkshop.workorder.infrastructure.metrics;

import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;

@ApplicationScoped
public class WorkOrderMetricsAdapter implements WorkOrderMetricsPort {

    static final String STATUS_CHANGES = "workorder.status.changes";
    static final String COMPLETION_TIME = "workorder.completion.time";
    static final String TIME_TO_STATUS = "workorder.time.to.status";
    static final String UNKNOWN_BRANCH = "unknown";

    private final MeterRegistry registry;

    public WorkOrderMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordStatusChange(StatusWO status, String branchId) {
        if (status == null) {
            return;
        }
        registry.counter(STATUS_CHANGES, "status", status.name(), "branch", branch(branchId)).increment();
    }

    @Override
    public void recordCompletion(String branchId, Duration serviceDuration) {
        if (serviceDuration == null || serviceDuration.isNegative()) {
            return;
        }
        Timer.builder(COMPLETION_TIME)
                .description("Elapsed time from work order creation to service completion")
                .tag("branch", branch(branchId))
                .register(registry)
                .record(serviceDuration);
    }

    @Override
    public void recordTimeToStatus(StatusWO status, String branchId, Duration sinceCreation) {
        if (status == null || sinceCreation == null || sinceCreation.isNegative()) {
            return;
        }
        Timer.builder(TIME_TO_STATUS)
                .description("Elapsed time from work order creation until it reached a given status")
                .tag("status", status.name())
                .tag("branch", branch(branchId))
                .register(registry)
                .record(sinceCreation);
    }

    // Tag ausente faz o Micrometer recusar a serie inteira quando a mesma metrica ja foi registrada com a tag.
    private static String branch(String branchId) {
        return branchId == null || branchId.isBlank() ? UNKNOWN_BRANCH : branchId;
    }
}
