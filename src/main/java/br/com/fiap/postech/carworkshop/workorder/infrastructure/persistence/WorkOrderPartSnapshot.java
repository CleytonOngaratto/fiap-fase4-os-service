package br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence;

import br.com.fiap.postech.carworkshop.inventory.domain.entity.TypeProductEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderPartSnapshot {

    @Column(name = "parts_and_supply_id")
    private Long partsAndSupplyId;

    private String code;

    private String manufacturer;

    private String description;

    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private TypeProductEnum type;

    // Estoque da peça na criação da OS, não a quantidade consumida por ela.
    private Integer quantity;
}
