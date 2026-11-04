# language: pt
Funcionalidade: Caminho feliz da ordem de serviço
  A OS atravessa os três serviços. O cliente paga na aprovação, as peças são reservadas e depois
  baixadas, e o reparo concluído é o pivô: dali em diante nada mais se desfaz.

  @caminho-feliz @e2e @pagamento
  Cenário: da abertura à entrega do veículo
    Dado uma OS aberta na filial "SP-01"
    Então a OS fica "RECEIVED"
    Quando o mecânico inicia o diagnóstico
    Então a OS fica "UNDER_DIAGNOSIS"
    Quando o mecânico conclui o diagnóstico
    Então a OS fica "PENDING_APPROVAL"
    E o orçamento fica "PENDING_APPROVAL"
    Quando o cliente aprova o orçamento
    Então a OS fica "AWAITING_PAYMENT"
    E o estoque da peça fica em 10 com 2 reservadas
    Quando o cliente paga
    Então a OS fica "IN_PROGRESS"
    E o pagamento fica "CONFIRMED"
    E o estoque da peça fica em 8 com 0 reservadas
    Quando o mecânico inicia o reparo
    E o mecânico conclui o reparo
    Então a OS fica "COMPLETED"
    Quando o atendimento registra a entrega
    Então a OS fica "DELIVERED"
    E o histórico da OS registra "RECEIVED, UNDER_DIAGNOSIS, PENDING_APPROVAL, AWAITING_PAYMENT, IN_PROGRESS, COMPLETED, DELIVERED"
    E o cancelamento da OS é recusado com 409
