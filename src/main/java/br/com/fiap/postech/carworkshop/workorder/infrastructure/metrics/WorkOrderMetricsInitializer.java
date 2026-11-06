package br.com.fiap.postech.carworkshop.workorder.infrastructure.metrics;

import br.com.fiap.postech.carworkshop.branch.domain.entity.Branch;
import br.com.fiap.postech.carworkshop.branch.usecase.port.out.BranchRepositoryPort;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@ApplicationScoped
public class WorkOrderMetricsInitializer {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderMetricsInitializer.class);

    @Inject
    BranchReader branches;

    @Inject
    WorkOrderMetricsAdapter metrics;

    void registerSeries(@Observes StartupEvent event) {
        try {
            metrics.registerSeries(branches.ids());
        } catch (RuntimeException e) {
            log.warn("Work order metrics were not registered ahead of the first change: {}", e.toString());
        }
    }

    // Bean separado: o StartupEvent nao tem contexto de requisicao, e a leitura das filiais so abre sessao numa
    // transacao, que a autoinvocacao ignoraria em silencio.
    @ApplicationScoped
    public static class BranchReader {

        @Inject
        BranchRepositoryPort repository;

        @Transactional
        public List<String> ids() {
            return repository.findAll().stream().map(Branch::getId).toList();
        }
    }
}
