# language: pt
Funcionalidade: Resiliência da saga
  Participante fora do ar, resposta impossível de processar, mensagem repetida e prazo disputando com
  evento não podem perder nem duplicar efeito. Nos cenários da F11 a ordem é forçada, em uma thread só:
  eles provam o versionamento e a convergência, não a corrida em si.

  @F9 @e2e @infra
  Cenário: o Execution fica fora do ar e a saga retoma quando ele volta
    Dado o Execution está fora do ar
    E uma OS aberta na filial "SP-01"
    Então o pedido de diagnóstico espera na fila do Execution
    E a OS fica "RECEIVED"
    Quando o Execution volta
    E o mecânico inicia o diagnóstico
    Então a OS fica "UNDER_DIAGNOSIS"
    Quando o atendimento cancela a OS
    Então a OS termina "CANCELED" com motivo "CANCELED_BY_STAFF"

  @F9
  Cenário: uma resposta impossível de processar não se perde
    Dado uma OS na etapa "aguardando diagnóstico"
    Quando chega uma resposta que o orquestrador não consegue processar
    Então a resposta vai intacta para a DLQ depois de esgotar as tentativas
    E a OS fica "RECEIVED"
    Quando o mecânico inicia o diagnóstico
    Então a OS fica "UNDER_DIAGNOSIS"
    Quando o atendimento cancela a OS
    Então a OS termina "CANCELED" com motivo "CANCELED_BY_STAFF"

  @F10
  Cenário: a mesma resposta chega duas vezes
    Dado uma OS na etapa "orçamento aguardando aprovação"
    Quando a última resposta chega de novo
    Então a resposta repetida não tem efeito
    E a OS fica "PENDING_APPROVAL"
    Quando o atendimento cancela a OS
    Então a OS termina "CANCELED" com motivo "CANCELED_BY_STAFF"

  @F11
  Cenário: o prazo comita antes da confirmação do pagamento
    Dado uma OS só com serviços na etapa "pagamento pendente"
    Quando a confirmação do pagamento perde a corrida para o prazo
    Então a transação da confirmação reverte por conflito de versão
    E a OS fica "AWAITING_PAYMENT"
    Quando a confirmação do pagamento é reentregue
    Então a OS termina "CANCELED" com motivo "PAYMENT_EXPIRED"
    E o pagamento fica "REFUNDED"

  @F11
  Cenário: a confirmação do pagamento comita antes da varredura do prazo
    Dado uma OS só com serviços na etapa "pagamento pendente"
    Quando a confirmação do pagamento ganha a corrida contra o prazo
    Então a OS fica "IN_PROGRESS"
    E o pagamento fica "CONFIRMED"
    E nenhuma compensação foi emitida
    Quando o atendimento cancela a OS
    Então a OS termina "CANCELED" com motivo "CANCELED_BY_STAFF"
    E o pagamento fica "REFUNDED"
