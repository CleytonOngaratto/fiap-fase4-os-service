package br.com.fiap.postech.carworkshop.workorder.usecase.port.out;

import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;

import java.time.Duration;

public interface WorkOrderMetricsPort {

    void recordStatusChange(StatusWO status);

    void recordCompletion(Duration serviceDuration);

    void recordTimeToStatus(StatusWO status, Duration sinceCreation);
}
