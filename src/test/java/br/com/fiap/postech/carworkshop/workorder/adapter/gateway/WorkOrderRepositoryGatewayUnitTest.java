package br.com.fiap.postech.carworkshop.workorder.adapter.gateway;

import br.com.fiap.postech.carworkshop.autoservice.domain.entity.AutoService;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.mapper.WorkOrderJpaMapper;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.WorkOrderJpaEntity;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.WorkOrderPanacheRepository;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.WorkOrderPartSnapshot;
import br.com.fiap.postech.carworkshop.workorder.infrastructure.persistence.WorkOrderServiceSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderRepositoryGatewayUnitTest {

    private final WorkOrderPanacheRepository repository = mock(WorkOrderPanacheRepository.class);
    private final WorkOrderJpaMapper mapper = mock(WorkOrderJpaMapper.class);
    private final WorkOrderRepositoryGateway gateway = new WorkOrderRepositoryGateway();

    private WorkOrderJpaEntity stored;

    @BeforeEach
    void wireTheCollaborators() {
        gateway.repository = repository;
        gateway.mapper = mapper;
        stored = new WorkOrderJpaEntity();
        when(repository.findById(7L)).thenReturn(stored);
        when(mapper.toDomain(stored)).thenReturn(WorkOrder.builder().id(7L).build());
    }

    @Test
    void saveCopiesTheDiagnosedItemsOntoTheStoredEntity() {
        AutoService service = AutoService.builder()
                .id(10L).description("Troca de Óleo").price(new BigDecimal("150.00")).build();
        PartsAndSupply part = PartsAndSupply.builder()
                .id(20L).description("Filtro de óleo").price(new BigDecimal("35.90")).quantity(3).build();
        List<WorkOrderServiceSnapshot> serviceSnapshots = new ArrayList<>();
        List<WorkOrderPartSnapshot> partSnapshots = new ArrayList<>();
        when(mapper.toServiceSnapshots(List.of(service))).thenReturn(serviceSnapshots);
        when(mapper.toPartSnapshots(List.of(part))).thenReturn(partSnapshots);

        gateway.save(WorkOrder.builder()
                .id(7L)
                .status(StatusWO.UNDER_DIAGNOSIS)
                .diagnosticDescription("Oleo vencido")
                .budgetValue(new BigDecimal("257.70"))
                .services(List.of(service))
                .parts(List.of(part))
                .build());

        assertSame(serviceSnapshots, stored.getServices());
        assertSame(partSnapshots, stored.getParts());
        assertEquals(StatusWO.UNDER_DIAGNOSIS, stored.getStatus());
        assertEquals("Oleo vencido", stored.getDiagnosticDescription());
        assertEquals(new BigDecimal("257.70"), stored.getBudgetValue());
    }

    @Test
    void saveLeavesTheStoredItemsAloneWhenTheWorkOrderCarriesNone() {
        gateway.save(WorkOrder.builder().id(7L).status(StatusWO.RECEIVED).build());

        assertNull(stored.getServices());
        assertNull(stored.getParts());
        verify(mapper, never()).toServiceSnapshots(anyList());
        verify(mapper, never()).toPartSnapshots(anyList());
    }
}
