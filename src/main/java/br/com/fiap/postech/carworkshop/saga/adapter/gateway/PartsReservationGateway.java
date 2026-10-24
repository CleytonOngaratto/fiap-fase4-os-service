package br.com.fiap.postech.carworkshop.saga.adapter.gateway;

import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.inventory.usecase.port.in.InventoryUseCase;
import br.com.fiap.postech.carworkshop.inventory.usecase.port.out.InventoryRepositoryPort;
import br.com.fiap.postech.carworkshop.saga.usecase.port.out.PartsReservationPort;
import br.com.fiap.postech.carworkshop.shared.domain.exception.EntityNotFoundException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.StockException;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.ObjIntConsumer;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class PartsReservationGateway implements PartsReservationPort {

    @Inject
    WorkOrderRepositoryPort workOrders;

    @Inject
    InventoryRepositoryPort inventoryRepository;

    @Inject
    InventoryUseCase inventory;

    @Override
    // MANDATORY, como no outbox: o InventoryUseCase vem de @Produces (o CDI nao intercepta) e o gateway de
    // estoque abre transacao propria se chamado fora de uma, o que deixaria a reserva comitada sozinha,
    // fora do passo da saga.
    @Transactional(Transactional.TxType.MANDATORY)
    public void reserve(Long workOrderId) {
        Map<Long, Integer> wanted = requestedParts(workOrderId);
        requireAvailability(wanted);
        wanted.forEach(inventory::reserve);
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void confirm(Long workOrderId) {
        apply(workOrderId, inventory::confirmReservation);
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void release(Long workOrderId) {
        apply(workOrderId, inventory::releaseReservation);
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void restore(Long workOrderId) {
        apply(workOrderId, inventory::restore);
    }

    private void apply(Long workOrderId, ObjIntConsumer<Long> operation) {
        requestedParts(workOrderId).forEach(operation::accept);
    }

    // Valida TODAS as pecas antes de mexer em qualquer uma: a falha vira compensacao (OUT_OF_STOCK), e sem
    // esta passagem previa a excecao deixaria parte das reservas aplicadas na mesma transacao.
    private void requireAvailability(Map<Long, Integer> wanted) {
        if (wanted.isEmpty()) {
            return;
        }
        Map<Long, PartsAndSupply> stock = inventoryRepository.findByIds(List.copyOf(wanted.keySet()))
                .stream().collect(Collectors.toMap(PartsAndSupply::getId, part -> part));

        for (Map.Entry<Long, Integer> item : wanted.entrySet()) {
            PartsAndSupply part = stock.get(item.getKey());
            if (part == null) {
                throw new EntityNotFoundException("Part not found: " + item.getKey());
            }
            if (part.availableQuantity() < item.getValue()) {
                throw new StockException("Insufficient stock for part: " + part.getDescription());
            }
        }
    }

    private Map<Long, Integer> requestedParts(Long workOrderId) {
        return workOrders.findById(workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Work Order not found."))
                .getParts().stream()
                .collect(Collectors.toMap(PartsAndSupply::getId,
                        part -> part.getQuantity() == null ? 1 : part.getQuantity(),
                        Integer::sum));
    }
}
