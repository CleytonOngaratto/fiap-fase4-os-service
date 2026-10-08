# Máquina de estados da Ordem de Serviço — v2 (Fase 4)

> Escrito no B0 (2026-10-01), revisado pelo dono em 2026-10-05.
> Complementa o [catálogo de eventos v1](catalogo-de-mensagens.md), a
> [matriz de compensação](matriz-de-compensacao.md) e o [ADR da Saga](adr/ADR-001-saga-orquestrada.md).

Na Fase 4 existem **três máquinas de estado** diferentes, e misturá-las é a forma mais rápida de errar:

| Máquina | Onde vive | Para quem é |
|---|---|---|
| **Status da OS** | OS Service (Postgres, tabela `work_orders`) | Cliente e atendimento: é o que `/tracking` e o histórico mostram |
| **Estado da saga** | OS Service (Postgres, tabela `saga_instance`) | O orquestrador: em que passo do processo distribuído a OS está e o que falta compensar |
| **Estados locais dos participantes** | Billing (Postgres) e Execution (DynamoDB) | Cada serviço, para garantir as próprias regras (*semantic lock*) |

O status da OS é uma **projeção** do processo: muda quando a saga confirma um fato, nunca antes.

---

## 1. Status da OS (visível ao cliente)

Mudança em relação à Fase 3: entra **um** status novo, `AWAITING_PAYMENT`. Os demais já existiam no enum
`StatusWO`, então os dashboards por status da Fase 3 continuam válidos.

```mermaid
stateDiagram-v2
    [*] --> RECEIVED: OS aberta (cliente + veículo + relato + filial)
    RECEIVED --> UNDER_DIAGNOSIS: DiagnosisStarted
    UNDER_DIAGNOSIS --> PENDING_APPROVAL: BudgetCreated
    PENDING_APPROVAL --> AWAITING_PAYMENT: BudgetApproved + peças reservadas
    AWAITING_PAYMENT --> IN_PROGRESS: PaymentConfirmed + peças baixadas
    IN_PROGRESS --> COMPLETED: RepairCompleted (pivô)
    COMPLETED --> DELIVERED: entrega registrada
    RECEIVED --> CANCELED: compensação concluída
    UNDER_DIAGNOSIS --> CANCELED: compensação concluída
    PENDING_APPROVAL --> CANCELED: compensação concluída
    AWAITING_PAYMENT --> CANCELED: compensação concluída
    IN_PROGRESS --> CANCELED: compensação concluída
    DELIVERED --> [*]
    CANCELED --> [*]
```

| Status | Significado | Entra quando |
|---|---|---|
| `RECEIVED` | OS aberta, aguardando o diagnóstico começar | abertura (cliente, veículo, relato do problema e filial) |
| `UNDER_DIAGNOSIS` | Mecânico diagnosticando | evento `DiagnosisStarted` |
| `PENDING_APPROVAL` | Orçamento enviado, aguardando o cliente | evento `BudgetCreated` |
| `AWAITING_PAYMENT` | Aprovado, peças reservadas, link de pagamento disponível | `BudgetApproved` + reserva local bem-sucedida |
| `IN_PROGRESS` | Pago, peças baixadas, reparo na fila ou em execução | `PaymentConfirmed` + baixa local |
| `COMPLETED` | Reparo concluído — **ponto sem volta** | evento `RepairCompleted` |
| `DELIVERED` | Veículo entregue | ação do atendimento (ADMIN) |
| `CANCELED` | Processo desfeito **e todas as compensações confirmadas** | saga chega a `COMPENSATED` |

- `CANCELED` só aparece **depois** que todas as compensações foram confirmadas. Enquanto a saga compensa, a OS
  mantém o status anterior e o estado da saga mostra `COMPENSATING`. Assim, o cliente nunca vê "cancelado"
  com o dinheiro ainda retido.
- Toda OS cancelada guarda um **motivo**: `REJECTED_BY_CUSTOMER`, `APPROVAL_EXPIRED`, `OUT_OF_STOCK`,
  `PAYMENT_EXPIRED`, `PAYMENT_REQUEST_FAILED`, `REPAIR_FAILED`, `DIAGNOSIS_FAILED`, `BUDGET_CREATION_FAILED` ou `CANCELED_BY_STAFF` (cancelamento pelo atendimento, permitido em qualquer estado antes do pivô).
- Toda transição grava uma linha em `work_order_status_history` (status, instante, motivo, `sagaId`). É o que
  atende "consulta de status e histórico" do edital e fecha a lacuna da Fase 3, que só tinha criação e fim.

---

## 2. Estado da saga (orquestrador, dentro do OS Service)

Persistido em `saga_instance` (uma por OS) com **controle de versão otimista** (`@Version`): o *scheduler* de
prazos e o consumidor de mensagens podem tentar mexer na mesma saga ao mesmo tempo, e só um vence. O outro
relê o estado e decide de novo.

```mermaid
stateDiagram-v2
    [*] --> AWAITING_DIAGNOSIS: OS aberta / StartDiagnosis
    AWAITING_DIAGNOSIS --> DIAGNOSING: DiagnosisStarted
    DIAGNOSING --> AWAITING_BUDGET: DiagnosisCompleted / precifica + CreateBudget
    AWAITING_BUDGET --> AWAITING_APPROVAL: BudgetCreated (prazo de aprovação)
    AWAITING_APPROVAL --> AWAITING_PAYMENT_LINK: BudgetApproved / reserva + RequestPayment
    AWAITING_PAYMENT_LINK --> AWAITING_PAYMENT: PaymentRequested (prazo de pagamento)
    AWAITING_PAYMENT --> AWAITING_REPAIR: PaymentConfirmed / baixa + StartRepair
    AWAITING_REPAIR --> REPAIRING: RepairStarted
    REPAIRING --> COMPLETED: RepairCompleted
    DIAGNOSING --> COMPENSATING: DiagnosisFailed
    AWAITING_BUDGET --> COMPENSATING: BudgetCreationFailed
    AWAITING_APPROVAL --> COMPENSATING: rejeição ou prazo vencido
    AWAITING_PAYMENT_LINK --> COMPENSATING: estoque insuficiente ou falha ao criar cobrança
    AWAITING_PAYMENT --> COMPENSATING: prazo de pagamento vencido
    REPAIRING --> COMPENSATING: RepairFailed
    note right of COMPENSATING: cancelamento ADMIN leva qualquer estado anterior a COMPLETED para COMPENSATING
    COMPENSATING --> COMPENSATED: todas as compensações confirmadas
    COMPLETED --> [*]
    COMPENSATED --> [*]
```

**Transições atômicas locais.** Os passos que acontecem **dentro** do OS Service — precificar os itens,
reservar, baixar, liberar ou devolver peças — não viram mensagem. Eles acontecem **na mesma transação
Postgres** que avança a saga e grava o próximo comando no outbox. Exemplo, ao receber `BudgetApproved`:

```
BEGIN
  reservar as peças do orçamento                  -- falhou? → estado COMPENSATING + outbox CancelBudget
  saga_instance: AWAITING_APPROVAL → AWAITING_PAYMENT_LINK (version + 1)
  work_orders: PENDING_APPROVAL → AWAITING_PAYMENT  (+ linha no histórico)
  outbox: RequestPayment
  inbox: registrar o messageId de BudgetApproved
COMMIT
```

Ou acontece tudo ou nada acontece. É o ACID local que sustenta cada passo da saga.

**Prazos** (configuráveis por variável de ambiente; demo = 2 min):

| Estado | Prazo padrão | Ao vencer |
|---|---|---|
| `AWAITING_APPROVAL` | 48 h | compensa com motivo `APPROVAL_EXPIRED` |
| `AWAITING_PAYMENT` | 30 min | compensa com motivo `PAYMENT_EXPIRED` (tentativas recusadas ficam registradas, mas não encerram a espera) |

O *scheduler* de prazos busca as sagas vencidas com `FOR UPDATE SKIP LOCKED`, para que várias réplicas não
compensem a mesma saga duas vezes. Esperas sem prazo de negócio (diagnóstico, que depende do mecânico, e
respostas de serviços fora do ar) **não compensam sozinhas**: geram alerta de "saga parada" na observabilidade.

**Etapas pela classificação de Garcia-Molina / Richardson:**

| Tipo | Etapas |
|---|---|
| Compensáveis | abrir OS · diagnóstico · orçamento · reserva · cobrança · baixa de peças · reparo em andamento |
| **Pivô** | reparo concluído (`RepairCompleted`) |
| Retentáveis | notificar o cliente · registrar a entrega |

---

## 3. Estados locais dos participantes

### Billing (Postgres)

```mermaid
stateDiagram-v2
    state "Orçamento" as B {
        [*] --> PENDING_APPROVAL: CreateBudget
        PENDING_APPROVAL --> APPROVED: cliente aprova
        PENDING_APPROVAL --> REJECTED: cliente rejeita
        PENDING_APPROVAL --> CANCELED: CancelBudget
        APPROVED --> CANCELED: CancelBudget
    }
```

```mermaid
stateDiagram-v2
    state "Pagamento" as P {
        [*] --> PENDING: RequestPayment (preferência criada)
        PENDING --> CONFIRMED: webhook + GET /v1/payments/{id} aprovado
        PENDING --> CANCELED: CancelPayment (prazo vencido)
        CONFIRMED --> REFUNDED: RefundPayment
        CANCELED --> REFUNDED: pagamento tardio (estorno automático)
    }
```

- *Semantic lock*: aprovar exige `PENDING_APPROVAL`; um orçamento `CANCELED` responde **409** à aprovação
  tardia.
- Tentativas recusadas (`rejected` no Mercado Pago) viram linhas em `payment_attempts` e o evento informativo
  `PaymentAttemptRejected`. **Não** mudam o estado do pagamento.

### Execution (DynamoDB — item `STATE` da OS)

```mermaid
stateDiagram-v2
    [*] --> DIAGNOSIS_QUEUED: StartDiagnosis
    DIAGNOSIS_QUEUED --> DIAGNOSIS_IN_PROGRESS: mecânico inicia
    DIAGNOSIS_IN_PROGRESS --> DIAGNOSIS_DONE: mecânico conclui (itens validados)
    DIAGNOSIS_IN_PROGRESS --> DIAGNOSIS_FAILED: mecânico registra diagnóstico inviável
    DIAGNOSIS_DONE --> REPAIR_QUEUED: StartRepair
    REPAIR_QUEUED --> REPAIR_IN_PROGRESS: mecânico inicia
    REPAIR_IN_PROGRESS --> REPAIR_DONE: mecânico conclui
    REPAIR_IN_PROGRESS --> REPAIR_FAILED: mecânico registra falha
    DIAGNOSIS_QUEUED --> CANCELED: CancelDiagnosis
    DIAGNOSIS_IN_PROGRESS --> CANCELED: CancelDiagnosis
    DIAGNOSIS_DONE --> CANCELED: CancelDiagnosis
```

Cada transição é um `UpdateItem` condicional (`ConditionExpression` no estado atual) no **mesmo**
`TransactWriteItems` que grava o item de outbox — o padrão provado no spike de 2026-09-29.

---

## O que mudou em relação à Fase 3

| Fase 3 | Fase 4 |
|---|---|
| Itens escolhidos na abertura | Itens definidos **no diagnóstico** e validados no catálogo do OS por REST síncrono |
| `DiagnosisSchedulerService` move `RECEIVED → UNDER_DIAGNOSIS` a cada 10 s | Diagnóstico é uma fila no Execution; o status muda pelo evento `DiagnosisStarted` |
| `completeDiagnosis` gera o orçamento na mesma chamada | Diagnóstico (Execution) e orçamento (Billing) são etapas separadas, ligadas pelo orquestrador |
| Aprovação em `/tracking/{id}/approve`, com baixa de estoque na mesma transação | Aprovação no Billing; reserva na aprovação, baixa no pagamento confirmado |
| Sem pagamento | Checkout Pro do Mercado Pago, com estorno como compensação |
| Histórico só com criação e fim | `work_order_status_history` com cada transição, motivo e `sagaId` |
