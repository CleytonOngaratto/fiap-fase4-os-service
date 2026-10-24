package br.com.fiap.postech.carworkshop.saga.domain.entity;

import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ConflictException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaInstance {

    private UUID sagaId;
    private Long workOrderId;
    private String branchId;
    private SagaState state;
    private CancelReason cancelReason;
    private String budgetId;
    private String paymentId;
    private Set<CompensationStep> pendingCompensations;
    private Instant deadlineAt;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public static SagaInstance start(Long workOrderId, String branchId) {
        Instant now = Instant.now();
        return SagaInstance.builder()
                .sagaId(UUID.randomUUID())
                .workOrderId(workOrderId)
                .branchId(branchId)
                .state(SagaState.AWAITING_DIAGNOSIS)
                .pendingCompensations(new LinkedHashSet<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public boolean advanceTo(SagaState next) {
        if (this.state == next || this.state.isCompensating() || this.state.isTerminal()) {
            return false;
        }
        this.state = next;
        this.deadlineAt = null;
        touch();
        return true;
    }

    public boolean advanceTo(SagaState next, Instant deadline) {
        if (!advanceTo(next)) {
            return false;
        }
        this.deadlineAt = deadline;
        return true;
    }

    public boolean isWaitingFor(SagaState expected) {
        return this.state == expected;
    }

    public boolean hasExpired(Instant now) {
        return this.deadlineAt != null && !this.state.isCompensating() && this.deadlineAt.isBefore(now);
    }

    // As compensacoes que faltam saem do estado atual, na ordem da matriz: e o que permite cancelar de
    // qualquer ponto antes do pivo sem desfazer o que nunca aconteceu.
    public List<CompensationStep> beginCompensation(CancelReason reason) {
        if (this.state.isPastPivot()) {
            throw new ConflictException(
                    "The repair is already complete: this saga can no longer be compensated.");
        }
        if (this.state.isCompensating()) {
            return List.of();
        }
        List<CompensationStep> steps = new ArrayList<>(this.state.compensations());
        this.state = SagaState.COMPENSATING;
        this.cancelReason = reason;
        this.deadlineAt = null;
        this.pendingCompensations = new LinkedHashSet<>(
                steps.stream().filter(CompensationStep::awaitsParticipant).toList());
        touch();
        return steps;
    }

    public boolean confirmCompensation(CompensationStep step) {
        if (this.pendingCompensations == null || !this.pendingCompensations.remove(step)) {
            return false;
        }
        touch();
        return true;
    }

    public boolean isCompensationComplete() {
        return this.state == SagaState.COMPENSATING
                && (this.pendingCompensations == null || this.pendingCompensations.isEmpty());
    }

    public boolean finishCompensation() {
        if (!isCompensationComplete()) {
            return false;
        }
        this.state = SagaState.COMPENSATED;
        touch();
        return true;
    }

    public void rememberBudget(String budgetId) {
        this.budgetId = budgetId;
        touch();
    }

    public void rememberPayment(String paymentId) {
        this.paymentId = paymentId;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
