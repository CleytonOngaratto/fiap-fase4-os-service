package br.com.fiap.postech.carworkshop.workorder.domain.entity;

import br.com.fiap.postech.carworkshop.autoservice.domain.entity.AutoService;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ConflictException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.InvalidOperationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrder {

    private Long id;
    private Long customerId;
    private String customerEmail;
    private String customerDocument;
    private Long vehicleId;
    private String vehiclePlate;
    private String branchId;
    private String complaint;
    private StatusWO status;
    private LocalDateTime creationDate;
    private LocalDateTime endDate;
    private String diagnosticDescription;
    private BigDecimal budgetValue;
    private LocalDateTime budgetApprovalDate;
    private CancelReason cancelReason;
    private List<AutoService> services;
    private List<PartsAndSupply> parts;
    private boolean deleted;

    public static WorkOrder create(Long customerId, String customerEmail, String customerDocument,
                                   Long vehicleId, String vehiclePlate,
                                   String branchId, String complaint) {
        return WorkOrder.builder()
                .customerId(customerId)
                .customerEmail(customerEmail)
                .customerDocument(customerDocument)
                .vehicleId(vehicleId)
                .vehiclePlate(vehiclePlate)
                .branchId(branchId)
                .complaint(complaint)
                .services(List.of())
                .parts(List.of())
                .status(StatusWO.RECEIVED)
                .creationDate(LocalDateTime.now())
                .build();
    }

    // As transicoes dirigidas pela saga devolvem boolean em vez de lancar: a resposta de um participante pode
    // ser reentregue, e lancar mandaria toda duplicata para a DLQ. As dirigidas por pessoa (entrega, exclusao)
    // lancam, porque quem chamou precisa do erro.
    public boolean startDiagnosis() {
        if (this.status != StatusWO.RECEIVED) {
            return false;
        }
        this.status = StatusWO.UNDER_DIAGNOSIS;
        return true;
    }

    public boolean priceDiagnosis(String description, List<AutoService> services,
                                  List<PartsAndSupply> parts, BigDecimal total) {
        if (this.status != StatusWO.UNDER_DIAGNOSIS || this.budgetValue != null) {
            return false;
        }
        this.diagnosticDescription = description;
        this.services = services != null ? services : List.of();
        this.parts = parts != null ? parts : List.of();
        this.budgetValue = total;
        return true;
    }

    public boolean pendApproval() {
        if (this.status != StatusWO.UNDER_DIAGNOSIS) {
            return false;
        }
        this.status = StatusWO.PENDING_APPROVAL;
        return true;
    }

    public boolean awaitPayment() {
        if (this.status != StatusWO.PENDING_APPROVAL) {
            return false;
        }
        this.status = StatusWO.AWAITING_PAYMENT;
        this.budgetApprovalDate = LocalDateTime.now();
        return true;
    }

    public boolean startRepair() {
        if (this.status != StatusWO.AWAITING_PAYMENT) {
            return false;
        }
        this.status = StatusWO.IN_PROGRESS;
        return true;
    }

    public boolean completeService() {
        if (this.status != StatusWO.IN_PROGRESS) {
            return false;
        }
        this.status = StatusWO.COMPLETED;
        this.endDate = LocalDateTime.now();
        return true;
    }

    public boolean cancel(CancelReason reason) {
        if (this.status == StatusWO.CANCELED) {
            return false;
        }
        if (isPastPivot()) {
            throw new ConflictException(
                    "Work order can no longer be canceled: the repair is already " + this.status + ".");
        }
        this.status = StatusWO.CANCELED;
        this.cancelReason = reason;
        this.endDate = LocalDateTime.now();
        return true;
    }

    public boolean isPastPivot() {
        return this.status == StatusWO.COMPLETED || this.status == StatusWO.DELIVERED;
    }

    public void deliverVehicle() {
        if (this.status != StatusWO.COMPLETED) {
            throw new InvalidOperationException("Vehicle can only be delivered when status is COMPLETED.");
        }
        this.status = StatusWO.DELIVERED;
    }

    public void softDelete() {
        if (this.status != StatusWO.COMPLETED && this.status != StatusWO.DELIVERED) {
            throw new InvalidOperationException("Only COMPLETED or DELIVERED work orders can be deleted.");
        }
        this.deleted = true;
    }

    public boolean isOwnedBy(String document) {
        return this.customerDocument != null && this.customerDocument.equals(document);
    }
}
