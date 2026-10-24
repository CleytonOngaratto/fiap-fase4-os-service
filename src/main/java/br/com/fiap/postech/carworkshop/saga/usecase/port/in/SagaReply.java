package br.com.fiap.postech.carworkshop.saga.usecase.port.in;

import br.com.fiap.postech.carworkshop.saga.domain.message.SagaItem;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

// Projecao plana do envelope: o consumidor le o JSON como tolerant reader e entrega aqui so os campos que o
// orquestrador usa, para que o caso de uso nao conheca Jackson nem o formato da mensagem.
@Builder
public record SagaReply(UUID messageId,
                        String type,
                        Long workOrderId,
                        String correlationId,
                        String description,
                        List<SagaItem> items,
                        String budgetId,
                        String paymentId,
                        String reason) {
}
