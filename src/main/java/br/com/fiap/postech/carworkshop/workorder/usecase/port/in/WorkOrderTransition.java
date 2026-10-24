package br.com.fiap.postech.carworkshop.workorder.usecase.port.in;

public enum WorkOrderTransition {
    START_DIAGNOSIS,
    PEND_APPROVAL,
    AWAIT_PAYMENT,
    START_REPAIR,
    COMPLETE,
    CANCEL
}
