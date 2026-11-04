# language: pt
Funcionalidade: Cancelamento pelo atendimento
  O ADMIN pode cancelar a OS em qualquer etapa antes do pivô. A saga manda exatamente as compensações que
  faltam a partir de onde está: nem desfaz o que nunca aconteceu, nem esquece o que já aconteceu.

  @F9b @e2e
  Esquema do Cenário: cancelar com a OS em "<etapa>"
    Dado uma OS na etapa "<etapa>"
    Quando o atendimento cancela a OS
    Então a OS termina "CANCELED" com motivo "CANCELED_BY_STAFF"
    E o orçamento fica "<orçamento>"
    E o item de execução fica "CANCELED"
    E o estoque da peça fica em 10 com 0 reservadas

    Exemplos:
      | etapa                          | orçamento   |
      | diagnóstico em andamento       | inexistente |
      | orçamento aguardando aprovação | CANCELED    |
      | pagamento pendente             | CANCELED    |

  @F9c @e2e
  Cenário: cancelar antes de o mecânico iniciar o diagnóstico tira o item da fila
    Dado uma OS na etapa "aguardando diagnóstico"
    Quando o atendimento cancela a OS
    Então a OS termina "CANCELED" com motivo "CANCELED_BY_STAFF"
    E o item de execução fica "CANCELED"
    E o orçamento fica "inexistente"

  @F9b
  Esquema do Cenário: as compensações emitidas ao cancelar em "<etapa>"
    Dado uma OS na etapa "<etapa>"
    Quando o atendimento cancela a OS
    Então a OS termina "CANCELED" com motivo "CANCELED_BY_STAFF"
    E as compensações emitidas são "<compensações>"

    Exemplos:
      | etapa                          | compensações                                 |
      | aguardando diagnóstico         | CancelDiagnosis                              |
      | diagnóstico em andamento       | CancelDiagnosis                              |
      | orçamento aguardando aprovação | CancelBudget, CancelDiagnosis                |
      | pagamento pendente             | CancelPayment, CancelBudget, CancelDiagnosis |
      | reparo em andamento            | RefundPayment, CancelBudget, CancelDiagnosis |
