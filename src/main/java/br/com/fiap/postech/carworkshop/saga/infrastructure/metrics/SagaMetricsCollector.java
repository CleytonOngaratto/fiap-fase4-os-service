package br.com.fiap.postech.carworkshop.saga.infrastructure.metrics;

import br.com.fiap.postech.carworkshop.branch.domain.entity.Branch;
import br.com.fiap.postech.carworkshop.branch.usecase.port.out.BranchRepositoryPort;
import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaState;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaCount;
import br.com.fiap.postech.carworkshop.saga.infrastructure.persistence.SagaInstancePanacheRepository;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SagaMetricsCollector {

    static final String INSTANCES = "saga.instances";
    static final String COMPENSATIONS = "saga.compensations";
    static final String OVERDUE = "saga.overdue";
    static final Duration DEADLINE_GRACE = Duration.ofMinutes(2);
    static final Duration COMPENSATING_LIMIT = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(SagaMetricsCollector.class);
    private static final String BRANCH = "branch";

    @Inject
    SagaInstancePanacheRepository repository;

    @Inject
    BranchRepositoryPort branches;

    @Inject
    MeterRegistry registry;

    Clock clock = Clock.systemUTC();

    private final Map<Series, Double> values = new ConcurrentHashMap<>();

    @Scheduled(every = "30s", identity = "saga-metrics",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void collect() {
        try {
            publish(measure(clock.instant()));
        } catch (RuntimeException e) {
            values.replaceAll((series, value) -> Double.NaN);
            log.warn("Saga metrics could not be read; reporting them as unknown: {}", e.toString());
        }
    }

    // Zero explicito em toda combinacao: o GROUP BY so devolve o que existe, e a serie de um estado que esvaziou
    // ficaria parada no ultimo valor.
    private Map<Series, Double> measure(Instant now) {
        List<SagaCount> byState = repository.countByState();
        List<SagaCount> byReason = repository.countByCancelReason();
        List<SagaCount> overdue = repository.countOverdue(now.minus(DEADLINE_GRACE), now.minus(COMPENSATING_LIMIT));

        Map<Series, Double> measured = new HashMap<>();
        for (String branch : branchesOf(byState, byReason, overdue)) {
            for (SagaState state : SagaState.values()) {
                measured.put(Series.of(INSTANCES, "state", state.name(), branch), 0.0);
            }
            for (CancelReason reason : CancelReason.values()) {
                measured.put(Series.of(COMPENSATIONS, "reason", reason.name(), branch), 0.0);
            }
            measured.put(Series.overdue(branch), 0.0);
        }
        byState.forEach(count -> measured.merge(Series.of(INSTANCES, "state", count.key(), count.branchId()),
                (double) count.count(), Double::sum));
        byReason.forEach(count -> measured.merge(Series.of(COMPENSATIONS, "reason", count.key(), count.branchId()),
                (double) count.count(), Double::sum));
        overdue.forEach(count -> measured.merge(Series.overdue(count.branchId()), (double) count.count(), Double::sum));
        return measured;
    }

    private Set<String> branchesOf(List<SagaCount> byState, List<SagaCount> byReason, List<SagaCount> overdue) {
        Set<String> ids = new TreeSet<>();
        branches.findAll().stream().map(Branch::getId).forEach(ids::add);
        for (List<SagaCount> counts : List.of(byState, byReason, overdue)) {
            counts.forEach(count -> ids.add(count.branchId()));
        }
        return ids;
    }

    private void publish(Map<Series, Double> measured) {
        values.replaceAll((series, value) -> measured.getOrDefault(series, 0.0));
        measured.forEach((series, value) -> {
            if (values.putIfAbsent(series, value) == null) {
                Gauge.builder(series.name(), values, all -> all.getOrDefault(series, Double.NaN))
                        .tags(series.tags())
                        .register(registry);
            }
        });
    }

    private record Series(String name, Tags tags) {

        static Series of(String name, String key, String value, String branch) {
            return new Series(name, Tags.of(key, value, BRANCH, branch));
        }

        static Series overdue(String branch) {
            return new Series(OVERDUE, Tags.of(BRANCH, branch));
        }
    }
}
