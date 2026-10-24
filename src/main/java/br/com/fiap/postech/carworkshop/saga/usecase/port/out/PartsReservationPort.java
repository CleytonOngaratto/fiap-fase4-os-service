package br.com.fiap.postech.carworkshop.saga.usecase.port.out;

public interface PartsReservationPort {

    void reserve(Long workOrderId);

    void confirm(Long workOrderId);

    void release(Long workOrderId);

    void restore(Long workOrderId);
}
