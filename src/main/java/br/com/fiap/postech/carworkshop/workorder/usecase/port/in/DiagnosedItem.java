package br.com.fiap.postech.carworkshop.workorder.usecase.port.in;

public record DiagnosedItem(boolean part, Long catalogId, int quantity) {
}
