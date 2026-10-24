package br.com.fiap.postech.carworkshop.workorder.adapter.presenter;

import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record WorkOrderPartResponse(
        Long id,
        String code,
        String description,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        Integer quantity
) {
    public static WorkOrderPartResponse from(PartsAndSupply part) {
        return new WorkOrderPartResponse(part.getId(), part.getCode(), part.getDescription(),
                part.getPrice(), part.getQuantity());
    }
}
