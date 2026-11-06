package br.com.fiap.postech.carworkshop.saga.infrastructure.persistence;

import br.com.fiap.postech.carworkshop.saga.domain.entity.SagaState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SagaCountTest {

    @Test
    void readsAnEnumKeyByItsName() {
        assertEquals(new SagaCount("AWAITING_PAYMENT", "SP-01", 3),
                SagaCount.of(new Object[]{SagaState.AWAITING_PAYMENT, "SP-01", 3L}));
    }

    @Test
    void readsATextKeyAndAnyNumericCount() {
        assertEquals(new SagaCount("PAYMENT_EXPIRED", "RJ-01", 2),
                SagaCount.of(new Object[]{"PAYMENT_EXPIRED", "RJ-01", 2}));
    }

    @Test
    void namesAMissingBranchUnknown() {
        assertEquals(SagaCount.UNKNOWN_BRANCH, SagaCount.of(new Object[]{SagaState.COMPLETED, null, 1L}).branchId());
        assertEquals(SagaCount.UNKNOWN_BRANCH, SagaCount.of(new Object[]{SagaState.COMPLETED, " ", 1L}).branchId());
    }
}
