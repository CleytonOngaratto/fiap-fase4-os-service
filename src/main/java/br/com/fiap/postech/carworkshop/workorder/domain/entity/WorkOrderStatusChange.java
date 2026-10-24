package br.com.fiap.postech.carworkshop.workorder.domain.entity;

import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderStatusChange(Long workOrderId, String branchId, StatusWO status,
                                    CancelReason reason, UUID sagaId, Instant changedAt) {
}
