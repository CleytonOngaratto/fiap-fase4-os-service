package br.com.fiap.postech.carworkshop.workorder.adapter.presenter;

import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrderStatusChange;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record WorkOrderStatusChangeResponse(
        StatusWO status,
        CancelReason reason,
        @JsonProperty("changed_at") Instant changedAt
) {
    public static WorkOrderStatusChangeResponse from(WorkOrderStatusChange change) {
        return new WorkOrderStatusChangeResponse(change.status(), change.reason(), change.changedAt());
    }
}
