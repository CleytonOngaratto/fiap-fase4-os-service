package br.com.fiap.postech.carworkshop.inventory.usecase.interactor;

import br.com.fiap.postech.carworkshop.inventory.adapter.dto.PartsAndSupplyRequest;
import br.com.fiap.postech.carworkshop.inventory.adapter.presenter.PartsAndSupplyResponse;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.inventory.usecase.port.in.InventoryUseCase;
import br.com.fiap.postech.carworkshop.inventory.usecase.port.out.InventoryRepositoryPort;
import br.com.fiap.postech.carworkshop.shared.domain.exception.EntityNotFoundException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class InventoryInteractor implements InventoryUseCase {

    private final InventoryRepositoryPort repository;

    public InventoryInteractor(InventoryRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<PartsAndSupplyResponse> findAll() {
        log.info("Finding all parts and supplies");
        return repository.findAll().stream().map(PartsAndSupplyResponse::from).toList();
    }

    @Override
    public PartsAndSupplyResponse findById(Long id) {
        log.info("Finding part/supply by id: {}", id);
        validateId(id);
        return repository.findById(id)
                .map(PartsAndSupplyResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Part or Supply not found."));
    }

    @Override
    public PartsAndSupplyResponse create(PartsAndSupplyRequest request) {
        log.info("Creating part/supply: {}", request);
        validateRequest(request);
        PartsAndSupply part = PartsAndSupply.builder()
                .code(request.code())
                .manufacturer(request.manufacturer())
                .description(request.description())
                .price(request.price())
                .type(request.type())
                .quantity(request.quantity())
                .reservedQuantity(0)
                .build();
        return PartsAndSupplyResponse.from(repository.save(part));
    }

    @Override
    public void update(Long id, PartsAndSupplyRequest request) {
        log.info("Updating part/supply id: {}", id);
        validateId(id);
        validateRequest(request);
        PartsAndSupply existing = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Part or Supply not found."));
        existing.setCode(request.code());
        existing.setManufacturer(request.manufacturer());
        existing.setDescription(request.description());
        existing.setPrice(request.price());
        existing.setType(request.type());
        existing.changeStock(request.quantity());
        repository.save(existing);
    }

    @Override
    public void delete(Long id) {
        log.info("Deleting part/supply id: {}", id);
        validateId(id);
        if (!repository.deleteById(id)) {
            throw new EntityNotFoundException("Part or Supply not found.");
        }
    }

    @Override
    public void consumeStock(Long id, Integer quantity) {
        log.info("Consuming stock for part id: {}, quantity: {}", id, quantity);
        validateId(id);
        if (quantity == null || quantity <= 0) {
            throw new ValidationException("Quantity must be a positive integer.");
        }
        PartsAndSupply part = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Part or Supply not found."));
        part.consumeStock(quantity);
        repository.save(part);
    }

    @Override
    public void reserve(Long id, int quantity) {
        PartsAndSupply part = requirePart(id);
        part.reserve(quantity);
        repository.save(part);
        log.info("Reserved {} unit(s) of part {}.", quantity, id);
    }

    @Override
    public void confirmReservation(Long id, int quantity) {
        PartsAndSupply part = requirePart(id);
        part.confirmReservation(quantity);
        repository.save(part);
        log.info("Confirmed the reservation of {} unit(s) of part {}.", quantity, id);
    }

    @Override
    public void releaseReservation(Long id, int quantity) {
        PartsAndSupply part = requirePart(id);
        part.releaseReservation(quantity);
        repository.save(part);
        log.info("Released the reservation of {} unit(s) of part {}.", quantity, id);
    }

    @Override
    public void restore(Long id, int quantity) {
        PartsAndSupply part = requirePart(id);
        part.restore(quantity);
        repository.save(part);
        log.info("Restored {} unit(s) of part {} to stock.", quantity, id);
    }

    private PartsAndSupply requirePart(Long id) {
        validateId(id);
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Part or Supply not found."));
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Invalid ID: must be a positive number.");
        }
    }

    private void validateRequest(PartsAndSupplyRequest request) {
        if (request.description() == null || request.description().isBlank()) {
            throw new ValidationException("Part description must not be blank.");
        }
        if (request.price() == null || request.price().signum() <= 0) {
            throw new ValidationException("Part price must be greater than zero.");
        }
        if (request.quantity() == null || request.quantity() < 0) {
            throw new ValidationException("Quantity must be zero or positive.");
        }
    }
}
