package br.com.fiap.postech.carworkshop.workorder.usecase.port.out;

public interface WorkOrderNotificationPort {

    void notifyCompleted(CompletedNotification notification);

    record CompletedNotification(Long workOrderId, Long customerId, String customerEmail) {}
}
