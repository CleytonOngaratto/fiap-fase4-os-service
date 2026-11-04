# language: pt
Funcionalidade: Falhas no orçamento
  O orçamento existe e espera o cliente. Rejeitar, deixar o prazo vencer ou aprovar sem estoque levam ao
  mesmo desfecho: o orçamento é cancelado, o item sai da fila e nenhuma peça fica presa.

  @F1 @e2e
  Cenário: o cliente rejeita o orçamento
    Dado uma OS na etapa "orçamento aguardando aprovação"
    Quando o cliente rejeita o orçamento
    Então a OS termina "CANCELED" com motivo "REJECTED_BY_CUSTOMER"
    E o orçamento fica "CANCELED"
    E o item de execução fica "CANCELED"
    E o estoque da peça fica em 10 com 0 reservadas

  @F2 @e2e @prazo
  Cenário: o prazo de aprovação vence
    Dado uma OS na etapa "orçamento aguardando aprovação"
    Quando o prazo de aprovação vence
    Então a OS termina "CANCELED" com motivo "APPROVAL_EXPIRED"
    E o orçamento fica "CANCELED"
    E o item de execução fica "CANCELED"
    E o estoque da peça fica em 10 com 0 reservadas

  @F3 @e2e
  Cenário: o cliente aprova um orçamento com peça sem estoque
    Dado uma OS cujo orçamento inclui uma peça sem estoque
    Quando o cliente aprova o orçamento
    Então a OS termina "CANCELED" com motivo "OUT_OF_STOCK"
    E o orçamento fica "CANCELED"
    E o item de execução fica "CANCELED"
    E o estoque da peça fica em 0 com 0 reservadas
