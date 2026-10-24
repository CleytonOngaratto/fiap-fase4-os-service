package br.com.fiap.postech.carworkshop.saga.domain.message;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreateBudgetPayload(String customerDocument, List<SagaItem> items, BigDecimal total,
                                  Instant approvalDeadline) implements SagaPayload {
}
