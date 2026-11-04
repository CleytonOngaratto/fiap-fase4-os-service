# language: pt
Funcionalidade: Falhas no diagnóstico
  Antes do orçamento só existe o item na fila da filial. Não há dinheiro nem peça a devolver, mas o
  passo do diagnóstico ainda precisa se confirmar antes de a OS ser cancelada.

  @F0a @e2e
  Cenário: o mecânico registra que não há reparo viável
    Dado uma OS na etapa "diagnóstico em andamento"
    Quando o mecânico registra que o diagnóstico é inviável
    Então a OS termina "CANCELED" com motivo "DIAGNOSIS_FAILED"
    E o item de execução fica "DIAGNOSIS_FAILED"
    E o orçamento fica "inexistente"
    E o estoque da peça fica em 10 com 0 reservadas

  @F0b
  Cenário: o Billing recusa criar o orçamento
    Dado o Billing recusa criar orçamentos
    E uma OS na etapa "diagnóstico em andamento"
    Quando o mecânico conclui o diagnóstico
    Então a OS termina "CANCELED" com motivo "BUDGET_CREATION_FAILED"
    E o item de execução fica "CANCELED"
    E o orçamento fica "inexistente"
    E o estoque da peça fica em 10 com 0 reservadas

  @F12 @e2e
  Cenário: o diagnóstico cita um item que não existe no catálogo
    Dado uma OS na etapa "diagnóstico em andamento"
    Quando o mecânico conclui o diagnóstico com um item fora do catálogo
    Então o mecânico é avisado de qual item não existe
    E a OS fica "UNDER_DIAGNOSIS"
    Quando o atendimento cancela a OS
    Então a OS termina "CANCELED" com motivo "CANCELED_BY_STAFF"

  @F13
  Cenário: sem a conclusão do diagnóstico a saga espera
    Dado uma OS na etapa "diagnóstico em andamento"
    Então nenhum orçamento foi pedido
    E a OS fica "UNDER_DIAGNOSIS"
    Quando o atendimento cancela a OS
    Então a OS termina "CANCELED" com motivo "CANCELED_BY_STAFF"

  @F13 @e2e @e2e-only @infra
  Cenário: o OS está fora do ar quando o mecânico conclui o diagnóstico
    Dado uma OS na etapa "diagnóstico em andamento"
    E o OS está fora do ar
    Quando o mecânico tenta concluir o diagnóstico
    Então o mecânico recebe 503
    Quando o OS volta
    E o mecânico conclui o diagnóstico
    Então a OS fica "PENDING_APPROVAL"
    Quando o atendimento cancela a OS
    Então a OS termina "CANCELED" com motivo "CANCELED_BY_STAFF"
