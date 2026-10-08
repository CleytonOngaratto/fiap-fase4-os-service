# Matriz de compensação da Saga — final (Fase 4)

> Escrito no B0 (2026-10-01), revisado pelo dono em 2026-10-05.
> Mensagens: [catálogo v1](catalogo-de-mensagens.md) · Estados: [máquina de estados v2](maquina-de-estados.md).

**Cobertura de etapas:** toda etapa do caminho feliz tem pelo menos uma falha nesta tabela — diagnóstico (F0a), orçamento (F0b), aprovação (F1, F2), reserva (F3), cobrança (F4, F5, F7), reparo (F6) — e as falhas transitórias de qualquer etapa caem em F9. É o "rollback e compensação no caso de falha em qualquer etapa" do edital.

**Regra de prova:** uma linha só conta como pronta quando foi **executada** — por um cenário de BDD no PR
(componente, participantes simulados) **e** pelo roteiro e2e contra o EKS. "Implementado" não é "provado".

## Caminho feliz (referência)

| # | Etapa | Quem | Mecanismo | Status da OS | Tipo |
|---|---|---|---|---|---|
| 1 | Abrir OS (cliente + veículo + relato + filial) | OS | local + `StartDiagnosis` | `RECEIVED` | compensável |
| 2 | Mecânico inicia o diagnóstico | Execution | `DiagnosisStarted` | `UNDER_DIAGNOSIS` | compensável |
| 3 | Mecânico conclui: descrição + itens validados no catálogo (REST) | Execution | `DiagnosisCompleted` | `UNDER_DIAGNOSIS` | compensável |
| 4 | Precificar itens (preço congelado) e pedir orçamento | OS | local + `CreateBudget` | `UNDER_DIAGNOSIS` | — |
| 5 | Orçamento criado, prazo de aprovação aberto | Billing | `BudgetCreated` | `PENDING_APPROVAL` | compensável |
| 6 | Cliente aprova no Billing (`202`; o link aparece em `GET /budgets/{id}`) | Billing | `BudgetApproved` | `PENDING_APPROVAL` | — |
| 7 | Reservar peças e pedir a cobrança | OS | local + `RequestPayment` | `AWAITING_PAYMENT` | compensável |
| 8 | Preferência do Checkout Pro criada, prazo de pagamento aberto | Billing | `PaymentRequested` | `AWAITING_PAYMENT` | compensável |
| 9 | Pagamento confirmado (webhook assinado + `GET /v1/payments/{id}`) | Billing | `PaymentConfirmed` | `AWAITING_PAYMENT` | compensável |
| 10 | Baixar peças reservadas e pedir o reparo | OS | local + `StartRepair` | `IN_PROGRESS` | compensável |
| 11 | Mecânico executa o reparo | Execution | `RepairStarted` | `IN_PROGRESS` | compensável |
| 12 | **Reparo concluído** | Execution | `RepairCompleted` | `COMPLETED` | **pivô** |
| 13 | Notificar cliente · registrar entrega | OS | local | `DELIVERED` | retentável |

## Falhas

| # | Falha | Detectada por | Compensações, **na ordem** | Final — OS / Billing / Execution / Estoque | Como provar |
|---|---|---|---|---|---|
| F0a | **Diagnóstico inviável** (mecânico registra que não há reparo possível) | Execution (`DiagnosisFailed`) | cancela OS (não há orçamento, reserva nem pagamento a desfazer) | `CANCELED` (`DIAGNOSIS_FAILED`) / — / `DIAGNOSIS_FAILED` / intacto | BDD + e2e |
| F0b | **Billing recusa criar o orçamento** (regra de negócio) | Billing (`BudgetCreationFailed`) | `CancelDiagnosis` → cancela OS | `CANCELED` (`BUDGET_CREATION_FAILED`) / — / `CANCELED` / intacto | BDD |
| F1 | Cliente **rejeita** o orçamento | Billing (`BudgetRejected`) | `CancelDiagnosis` (fecha o item no Execution) → cancela OS | `CANCELED` (`REJECTED_BY_CUSTOMER`) / orçamento `REJECTED` / `CANCELED` / intacto | BDD + e2e |
| F2 | **Prazo de aprovação** vence | *scheduler* de prazos | `CancelBudget` (Billing passa a recusar aprovação tardia com 409) → `CancelDiagnosis` → cancela OS | `CANCELED` (`APPROVAL_EXPIRED`) / `CANCELED` / `CANCELED` / intacto | BDD + e2e (prazo de demo: 2 min) |
| F3 | **Estoque insuficiente** na reserva | OS, local (na transação do `BudgetApproved`) | `CancelBudget` → `CancelDiagnosis` → cancela OS | `CANCELED` (`OUT_OF_STOCK`) / `CANCELED` / `CANCELED` / intacto | BDD + e2e (peça com estoque 0) |
| F4 | **Prazo de pagamento** vence (recusas no meio só ficam registradas) | *scheduler* de prazos | `CancelPayment` (invalida a cobrança) → **libera reserva** (local) → `CancelBudget` → `CancelDiagnosis` → cancela OS | `CANCELED` (`PAYMENT_EXPIRED`) / pagamento `CANCELED`, orçamento `CANCELED` / `CANCELED` / reserva liberada | BDD + e2e (cartão `FUND` + prazo de demo) |
| F5 | **Falha ao criar a cobrança** (5xx do Mercado Pago esgotou o retry) | Billing (`PaymentRequestFailed`) | libera reserva → `CancelBudget` → `CancelDiagnosis` → cancela OS | `CANCELED` (`PAYMENT_REQUEST_FAILED`) / `CANCELED` / `CANCELED` / reserva liberada | BDD (MP simulado devolvendo 5xx) |
| F6 | **Falha no reparo** (antes de concluir) | Execution (`RepairFailed`) | `RefundPayment` (**estorno**) → **devolve ao estoque** (local) → `CancelBudget` → cancela OS | `CANCELED` (`REPAIR_FAILED`) / pagamento `REFUNDED`, orçamento `CANCELED` / `REPAIR_FAILED` / peças devolvidas | BDD + e2e (flag de falha da demo, só ADMIN) |
| F7 | **Pagamento confirmado depois da compensação** (corrida com o prazo) | orquestrador (`PaymentConfirmed` numa saga `COMPENSATING`/`COMPENSATED`) | `RefundPayment` **automático**, com motivo registrado | inalterado (`CANCELED`) / pagamento `REFUNDED` / inalterado / inalterado | BDD |
| F8 | **Estorno falha** (5xx do Mercado Pago) | Billing | retry com a **mesma** idempotency key; esgotou → DLQ + alerta + runbook. **A saga não "desiste" de compensar** | saga em `COMPENSATING` até resolver; alerta "saga parada" | BDD (MP simulado) + alerta provado no B8 |
| F9 | **Participante fora do ar** | SQS (a mensagem espera) | nenhuma — retry até voltar; esgotou `maxReceiveCount` → **DLQ + alerta**; retoma no redrive | saga pausada, depois segue do ponto onde parou | e2e (escala o participante para 0) |
| F9b | **Cancelamento pelo atendimento** (ADMIN), em qualquer estado antes do pivô | OS (endpoint ADMIN) | exatamente as compensações que faltam a partir do estado atual (as mesmas das linhas acima); depois do pivô → 409 | `CANCELED` (`CANCELED_BY_STAFF`) + o que o estado exigir nos outros serviços | BDD (em 3 estados) + e2e |
| F10 | **Mensagem duplicada** | inbox do consumidor | nenhuma — ignorada | inalterado | BDD |
| F11 | Corrida entre **prazo e evento** na mesma saga | versão otimista da `saga_instance` | quem perde relê o estado; se a saga já compensava, cai em F7 | consistente | BDD |
| F12 | Diagnóstico com **item inexistente** no catálogo | Execution, por REST síncrono ao OS | não entra na saga — **400** ao mecânico | inalterado | teste de contrato + BDD |
| F13 | **OS fora do ar** quando o mecânico conclui o diagnóstico | Execution (timeout/circuit breaker) | não entra na saga — **503** ao mecânico, que tenta de novo | inalterado | teste de componente |

## Invariantes que a matriz garante

1. **Nenhum dinheiro retido sem serviço:** todo caminho que cancela uma OS com pagamento confirmado passa por
   `RefundPayment` (F6, F7), e o estorno é retentado até confirmar (F8).
2. **Nenhuma peça presa:** toda reserva termina em baixa (pagamento confirmado) ou em liberação (F4, F5); toda
   baixa de uma OS cancelada termina em devolução (F6).
3. **`CANCELED` é verdade completa:** a OS só fica cancelada depois que **todas** as compensações foram
   confirmadas pelos participantes.
4. **Depois do pivô não se desfaz:** com o reparo concluído, falhas de notificação ou entrega só se retentam.
