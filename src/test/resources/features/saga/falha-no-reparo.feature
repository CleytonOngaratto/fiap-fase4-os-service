# language: pt
Funcionalidade: Falha no reparo
  O cliente já pagou e as peças já foram baixadas. Antes do pivô tudo ainda se desfaz: o dinheiro volta, as
  peças voltam ao estoque e a OS só vira cancelada depois de todas as confirmações.

  @F6
  Cenário: o reparo falha depois do pagamento
    Dado uma OS na etapa "reparo em andamento"
    Quando o mecânico registra que o reparo falhou
    Então a OS termina "CANCELED" com motivo "REPAIR_FAILED"
    E o pagamento fica "REFUNDED"
    E o orçamento fica "CANCELED"
    E o item de execução fica "REPAIR_FAILED"
    E o estoque da peça fica em 10 com 0 reservadas
