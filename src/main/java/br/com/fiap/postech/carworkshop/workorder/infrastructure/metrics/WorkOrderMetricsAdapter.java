package br.com.fiap.postech.carworkshop.workorder.infrastructure.metrics;

import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.Collection;

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
        statusChanges(status, branch(branchId)).increment();
    }

    @Override
    public void recordCompletion(String branchId, Duration serviceDuration) {
        if (serviceDuration == null || serviceDuration.isNegative()) {
            return;
        }
        completionTime(branch(branchId)).record(serviceDuration);
    }

    @Override
    public void recordTimeToStatus(StatusWO status, String branchId, Duration sinceCreation) {
        if (status == null || sinceCreation == null || sinceCreation.isNegative()) {
            return;
        }
        timeToStatus(status, branch(branchId)).record(sinceCreation);
    }

    // O New Relic so conta uma serie a partir da segunda leitura: registrar com zero antes do primeiro uso e o
    // que faz o primeiro incremento de cada status e filial aparecer.
    public void registerSeries(Collection<String> branchIds) {
        for (String branchId : branchIds) {
            String branch = branch(branchId);
            completionTime(branch);
            for (StatusWO status : StatusWO.values()) {
                statusChanges(status, branch);
                timeToStatus(status, branch);
            }
        }
    }

    private Counter statusChanges(StatusWO status, String branch) {
        return registry.counter(STATUS_CHANGES, "status", status.name(), "branch", branch);
    }

    private Timer completionTime(String branch) {
        return Timer.builder(COMPLETION_TIME)
                .description("Elapsed time from work order creation to service completion")
                .tag("branch", branch)
                .register(registry);
    }

    private Timer timeToStatus(StatusWO status, String branch) {
        return Timer.builder(TIME_TO_STATUS)
                .description("Elapsed time from work order creation until it reached a given status")
                .tag("status", status.name())
                .tag("branch", branch)
                .register(registry);
    }

    // Tag ausente faz o Micrometer recusar a serie inteira quando a mesma metrica ja foi registrada com a tag.
    private static String branch(String branchId) {
        return branchId == null || branchId.isBlank() ? UNKNOWN_BRANCH : branchId;
    }
}
