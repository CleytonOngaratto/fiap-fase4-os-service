package br.com.fiap.postech.carworkshop.inventory.adapter.gateway;

import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.TypeProductEnum;
import br.com.fiap.postech.carworkshop.inventory.infrastructure.mapper.PartsAndSupplyJpaMapper;
import br.com.fiap.postech.carworkshop.inventory.infrastructure.persistence.PartsAndSupplyJpaEntity;
import br.com.fiap.postech.carworkshop.inventory.infrastructure.persistence.PartsAndSupplyPanacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryRepositoryGatewayUnitTest {

    private final PartsAndSupplyPanacheRepository repository = mock(PartsAndSupplyPanacheRepository.class);
    private final PartsAndSupplyJpaMapper mapper = mock(PartsAndSupplyJpaMapper.class);
    private final InventoryRepositoryGateway gateway = new InventoryRepositoryGateway();

    private PartsAndSupplyJpaEntity stored;

    @BeforeEach
    void wireTheCollaborators() {
        gateway.repository = repository;
        gateway.mapper = mapper;
        stored = new PartsAndSupplyJpaEntity();
        when(repository.findById(1L)).thenReturn(stored);
        when(mapper.toDomain(stored)).thenReturn(PartsAndSupply.builder().id(1L).build());
    }

    @Test
    void saveCarriesTheReservationOntoTheStoredPart() {
        PartsAndSupply part = PartsAndSupply.builder()
                .id(1L).code("P001").manufacturer("Bosch").description("Filtro de óleo")
                .price(new BigDecimal("35.90")).type(TypeProductEnum.UNITARY)
                .quantity(10).reservedQuantity(0)
                .build();
        part.reserve(3);

        gateway.save(part);

        assertEquals(10, stored.getQuantity());
        assertEquals(3, stored.getReservedQuantity());
    }

    @Test
    void saveCarriesTheConsumptionOntoTheStoredPart() {
        PartsAndSupply part = PartsAndSupply.builder()
                .id(1L).code("P001").description("Filtro de óleo")
                .price(new BigDecimal("35.90")).type(TypeProductEnum.UNITARY)
                .quantity(10).reservedQuantity(3)
                .build();
        part.confirmReservation(3);

        gateway.save(part);

        assertEquals(7, stored.getQuantity());
        assertEquals(0, stored.getReservedQuantity());
    }
}
