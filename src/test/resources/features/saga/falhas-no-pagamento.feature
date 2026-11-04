# language: pt
Funcionalidade: Falhas no pagamento
  Com as peças reservadas e a cobrança aberta, a saga precisa devolver a reserva e nunca reter dinheiro: o
  pagamento que chega depois da compensação é estornado sem ninguém pedir, e o estorno nunca é abandonado.

  @F4 @e2e @prazo
  Cenário: o prazo de pagamento vence
    Dado uma OS na etapa "pagamento pendente"
    Quando o prazo de pagamento vence
    Então a OS termina "CANCELED" com motivo "PAYMENT_EXPIRED"
    E o pagamento fica "CANCELED"
    E o orçamento fica "CANCELED"
    E o item de execução fica "CANCELED"
    E o estoque da peça fica em 10 com 0 reservadas

  @F5
  Cenário: o provedor não consegue criar a cobrança
    Dado o provedor não consegue criar a cobrança
    E uma OS na etapa "orçamento aguardando aprovação"
    Quando o cliente aprova o orçamento
    Então a OS termina "CANCELED" com motivo "PAYMENT_REQUEST_FAILED"
    E o orçamento fica "CANCELED"
    E o item de execução fica "CANCELED"
    E o estoque da peça fica em 10 com 0 reservadas

  @F7
  Cenário: o pagamento é confirmado enquanto a saga compensa
    Dado uma OS na etapa "pagamento pendente"
    E os participantes demoram a confirmar as compensações
    Quando o prazo de pagamento vence
    E o cliente paga
    Então a OS fica "AWAITING_PAYMENT"
    Quando os participantes confirmam as compensações
    Então a OS termina "CANCELED" com motivo "PAYMENT_EXPIRED"
    E o pagamento fica "REFUNDED"
    E o estoque da peça fica em 10 com 0 reservadas

  @F7
  Cenário: o pagamento é confirmado depois que a saga já compensou
    Dado uma OS na etapa "pagamento pendente"
    Quando o prazo de pagamento vence
    Então a OS termina "CANCELED" com motivo "PAYMENT_EXPIRED"
    Quando o cliente paga
    Então o pagamento fica "REFUNDED"
    E a OS termina "CANCELED" com motivo "PAYMENT_EXPIRED"
    E o estoque da peça fica em 10 com 0 reservadas

  @F6 @F8 @e2e @pagamento
  Cenário: o provedor recusa o estorno e a saga não desiste
    Dado o provedor recusa o estorno
    E uma OS na etapa "reparo em andamento"
    Quando o mecânico registra que o reparo falhou
    Então o estorno fica pendente e visível
    E a OS fica "IN_PROGRESS"
    E o orçamento fica "CANCELED"
    E o item de execução fica "REPAIR_FAILED"
    E o estoque da peça fica em 10 com 0 reservadas
