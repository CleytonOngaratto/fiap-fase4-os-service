package br.com.fiap.postech.carworkshop.inventory.domain.entity;

import br.com.fiap.postech.carworkshop.shared.domain.exception.StockException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartsAndSupply {
    private Long id;
    private String code;
    private String manufacturer;
    private String description;
    private BigDecimal price;
    private TypeProductEnum type;
    private Integer quantity;
    private Integer reservedQuantity;

    public int availableQuantity() {
        return value(quantity) - value(reservedQuantity);
    }

    public void changeStock(Integer newQuantity) {
        if (newQuantity == null || newQuantity < 0) {
            throw new StockException("Quantity must be zero or positive.");
        }
        if (newQuantity < value(reservedQuantity)) {
            throw new StockException("There are " + value(reservedQuantity)
                    + " unit(s) reserved for part: " + this.description);
        }
        this.quantity = newQuantity;
    }

    public void reserve(int amount) {
        requirePositive(amount);
        if (availableQuantity() < amount) {
            throw new StockException("Insufficient stock for part: " + this.description);
        }
        this.reservedQuantity = value(reservedQuantity) + amount;
    }

    public void confirmReservation(int amount) {
        requirePositive(amount);
        requireReserved(amount);
        this.quantity = value(quantity) - amount;
        this.reservedQuantity = value(reservedQuantity) - amount;
    }

    public void releaseReservation(int amount) {
        requirePositive(amount);
        requireReserved(amount);
        this.reservedQuantity = value(reservedQuantity) - amount;
    }

    public void restore(int amount) {
        requirePositive(amount);
        this.quantity = value(quantity) + amount;
    }

    // Valida contra o disponivel, nao contra o total: senao uma baixa manual comeria unidade ja reservada
    // para uma OS em andamento e deixaria o disponivel negativo.
    public void consumeStock(int amount) {
        requirePositive(amount);
        if (availableQuantity() < amount) {
            throw new StockException("Insufficient stock for part: " + this.description);
        }
        this.quantity = value(quantity) - amount;
    }

    private void requireReserved(int amount) {
        if (value(reservedQuantity) < amount) {
            throw new StockException("There is no such reservation for part: " + this.description);
        }
    }

    private static void requirePositive(int amount) {
        if (amount <= 0) {
            throw new StockException("Quantity must be a positive integer.");
        }
    }

    private static int value(Integer amount) {
        return amount == null ? 0 : amount;
    }
}
