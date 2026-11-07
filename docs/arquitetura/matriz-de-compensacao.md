# Matriz de compensação da Saga — final (Fase 4)

> Escrito no B0 (2026-10-01), revisado pelo dono em 2026-10-05.
> Mensagens: [catálogo v1](catalogo-de-mensagens.md) · Estados: [máquina de estados v2](maquina-de-estados.md).

**Cobertura de etapas:** toda etapa do caminho feliz tem pelo menos uma falha nesta tabela — diagnóstico (F0a), orçamento (F0b), aprovação (F1, F2), reserva (F3), cobrança (F4, F5, F7), reparo (F6) — e as falhas transitórias de qualquer etapa caem em F9. É o "rollback e compensação no caso de falha em qualquer etapa" do edital.

**Regra de prova:** uma linha só conta como pronta quando foi **executada**. "Implementado" não é "provado". Toda linha
tem um cenário de componente, com participantes simulados por fila, que roda em todo PR. Quando dá para provocar a falha no
ambiente real, o **mesmo** cenário roda contra o EKS (tag `@e2e`). Onde não dá, a coluna "Como provar" diz por quê.
Cenários em [`src/test/resources/features/saga/`](../../src/test/resources/features/saga), uma tag por linha (`@F0a`…`@F13`).

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

| # | Falha | Detectada por | Compensações, na [ordem de emissão](sequencia.md#o-que-na-ordem-quer-dizer) | Final — OS / Billing / Execution / Estoque | Como provar |
|---|---|---|---|---|---|
| F0a | **Diagnóstico inviável** (mecânico registra que não há reparo possível) | Execution (`DiagnosisFailed`) | `CancelDiagnosis` (fecha o item na fila) → cancela OS. Não há orçamento, reserva nem pagamento a desfazer, mas o passo do diagnóstico **ainda se confirma** | `CANCELED` (`DIAGNOSIS_FAILED`) / — / `DIAGNOSIS_FAILED` / intacto | BDD + e2e (`POST /work-orders/{id}/diagnosis/fail`) |
| F0b | **Billing recusa criar o orçamento** (regra de negócio) | Billing (`BudgetCreationFailed`) | `CancelDiagnosis` → cancela OS | `CANCELED` (`BUDGET_CREATION_FAILED`) / — / `CANCELED` / intacto | BDD. Sem e2e: **inalcançável no EKS por construção**. O Execution recusa diagnóstico vazio, quantidade ≤ 0 e item fora do catálogo, e o catálogo recusa preço ≤ 0, então a regra do Billing nunca dispara |
| F1 | Cliente **rejeita** o orçamento | Billing (`BudgetRejected`) | `CancelBudget` (o orçamento sai de `REJECTED` para `CANCELED`, mantendo o motivo) → `CancelDiagnosis` (fecha o item no Execution) → cancela OS | `CANCELED` (`REJECTED_BY_CUSTOMER`) / orçamento `CANCELED` (`REJECTED_BY_CUSTOMER`) / `CANCELED` / intacto | BDD + e2e |
| F2 | **Prazo de aprovação** vence | *scheduler* de prazos | `CancelBudget` (Billing passa a recusar aprovação tardia com 409) → `CancelDiagnosis` → cancela OS | `CANCELED` (`APPROVAL_EXPIRED`) / `CANCELED` / `CANCELED` / intacto | BDD (prazo rebobinado no banco) + e2e (espera real, com `PT2M` por dispatch do `cd.yml`) |
| F3 | **Estoque insuficiente** na reserva | OS, local (na transação do `BudgetApproved`) | `CancelBudget` → `CancelDiagnosis` → cancela OS | `CANCELED` (`OUT_OF_STOCK`) / `CANCELED` / `CANCELED` / intacto | BDD + e2e (peça com estoque 0) |
| F4 | **Prazo de pagamento** vence (recusas no meio só ficam registradas) | *scheduler* de prazos | `CancelPayment` (invalida a cobrança) → **libera reserva** (local) → `CancelBudget` → `CancelDiagnosis` → cancela OS | `CANCELED` (`PAYMENT_EXPIRED`) / pagamento `CANCELED`, orçamento `CANCELED` / `CANCELED` / reserva liberada | **provado em ambiente real em 2026-10-29** (ver nota abaixo) + BDD + e2e (espera real, com `PT5M` por dispatch do `cd.yml`) |
| F5 | **Falha ao criar a cobrança** (5xx do Mercado Pago esgotou o retry) | Billing (`PaymentRequestFailed`) | libera reserva → `CancelBudget` → `CancelDiagnosis` → cancela OS | `CANCELED` (`PAYMENT_REQUEST_FAILED`) / `CANCELED` / `CANCELED` / reserva liberada | BDD (Billing simulado responde `PaymentRequestFailed`). O lado do Billing está em `MercadoPagoPaymentGatewayTest` e `PaymentInteractorTest`. Sem e2e: o provedor não devolve 5xx sob comando |
| F6 | **Falha no reparo** (antes de concluir) | Execution (`RepairFailed`) | `RefundPayment` (**estorno**) → **devolve ao estoque** (local) → `CancelBudget` → `CancelDiagnosis` → cancela OS | `CANCELED` (`REPAIR_FAILED`) / pagamento `REFUNDED`, orçamento `CANCELED` / `REPAIR_FAILED` / peças devolvidas | BDD (estorno simulado com sucesso) + e2e (`POST /work-orders/{id}/repair/fail`, só ADMIN). ⚠️ **No EKS termina no estado da F8:** a sandbox do provedor recusa todo estorno, então a saga fica em `COMPENSATING` com o `RefundPayment` na DLQ. O caminho até o `REFUNDED` só é provado com o estorno simulado |
| F7 | **Pagamento confirmado depois da compensação** (corrida com o prazo) | **duas camadas**: o Billing, quando o orçamento já está `CANCELED` ao chegar a notificação; o orquestrador, quando o `PaymentConfirmed` saiu antes disso | o Billing estorna sozinho (`PAID_AFTER_CANCELLATION`) e **não publica evento**; o orquestrador manda `RefundPayment` **automático**, respondido com `PaymentRefunded` | inalterado (`CANCELED`) / pagamento `REFUNDED` / inalterado / inalterado | BDD (duas ordens: saga em `COMPENSATING` e em `COMPENSATED`). A camada do Billing está em `PaymentNotificationInteractorTest`. Sem e2e: exigiria pagar depois do prazo, e o estorno cai no 401 da sandbox |
| F8 | **Estorno falha** (5xx do Mercado Pago) | Billing | retry com a **mesma** idempotency key; esgotou → DLQ + alerta + runbook. **A saga não "desiste" de compensar** | saga em `COMPENSATING` até resolver; alerta "saga parada" | **provado em ambiente real em 2026-10-29** (ver nota abaixo) + BDD (estorno recusado) + e2e (mesmo cenário da F6) + alerta no B8 |
| F9 | **Participante fora do ar** | SQS (a mensagem espera) | nenhuma — retry até voltar; esgotou `maxReceiveCount` → **DLQ + alerta**; retoma no redrive | saga pausada, depois segue do ponto onde parou | BDD (ninguém lê a fila, a saga pausa e retoma; e uma resposta impossível de processar vai intacta para a DLQ) + e2e (escala o participante para 0) |
| F9b | **Cancelamento pelo atendimento** (ADMIN), em qualquer estado antes do pivô | OS (endpoint ADMIN) | exatamente as compensações que faltam a partir do estado atual (as mesmas das linhas acima); depois do pivô → 409 | `CANCELED` (`CANCELED_BY_STAFF`) + o que o estado exigir nos outros serviços | BDD (3 estados, mais o conjunto exato de comandos em 5 estados) + e2e (os mesmos 3 estados) |
| F9c | **Cancelamento antes de o mecânico iniciar o diagnóstico** | OS (endpoint ADMIN ou prazo) | `CancelDiagnosis` → cancela OS. ⚠️ **Corrigido no bloco do Execution:** `AWAITING_DIAGNOSIS` tinha lista de compensações **vazia**, então a saga ia direto a `COMPENSATED` sem mandar nada — e o item ficava `DIAGNOSIS_QUEUED` na fila da filial para sempre, com um mecânico podendo pegar serviço de OS cancelada | `CANCELED` (`CANCELED_BY_STAFF`) / — / `CANCELED` / intacto | teste do orquestrador + BDD + e2e (item sai da fila) |
| F10 | **Mensagem duplicada** | inbox do consumidor | nenhuma — ignorada | inalterado | BDD (uma linha na inbox e a versão da saga não muda). Sem e2e: a repetição sob comando exigiria escrever na fila de respostas por fora |
| F11 | Corrida entre **prazo e evento** na mesma saga | versão otimista da `saga_instance` | quem perde reverte tudo; na reentrega relê o estado e, se a saga já compensava, cai em F7 | consistente | BDD, em **ordem forçada** (ver nota abaixo). Sem e2e: a corrida não se reproduz sob comando |
| F12 | Diagnóstico com **item inexistente** no catálogo | Execution, por `POST /internal/catalog/validate` no OS | não entra na saga — **400** ao mecânico, nomeando o item. O catálogo é consultado **antes** de qualquer escrita | inalterado | ✅ teste de contrato (WireMock) + BDD (o endpoint do OS nomeia o item) + e2e |
| F13 | **OS fora do ar** quando o mecânico conclui o diagnóstico | Execution (timeout, retry esgotado ou circuito aberto) | não entra na saga — **503** ao mecânico, que tenta de novo. Com o circuito aberto o 503 sai **sem nova chamada** | inalterado | ✅ teste de contrato (5xx, timeout, corpo malformado, circuito abrindo) + BDD fino (sem a conclusão, a saga espera e nenhum orçamento é pedido) + e2e (OS a 0 réplicas → 503 → volta) |

ℹ️ **Por que F7 tem duas camadas.** Quando a notificação do pagamento chega e o orçamento **já** está
`CANCELED`, o Billing sabe sozinho que aquele dinheiro não deve ficar: estorna com a mesma chave de
idempotência e registra o motivo. Não publica evento porque não há passo pendente — a saga já está
`COMPENSATED`, e um `PaymentRefunded` avulso seria resposta para quem não perguntou; no lugar dele ficam um
WARN com `sagaId` e `correlationId` e um contador. A camada do orquestrador continua necessária para a
corrida genuína, em que o Billing publicou `PaymentConfirmed` **antes** de o orçamento ser cancelado. Daí
também a regra de que um `RefundPayment` para pagamento **já** estornado ainda responde `PaymentRefunded`:
calar nesse caso deixaria a saga em `COMPENSATING` para sempre.

> **F11 em ordem forçada.** A corrida real depende de duas réplicas ao mesmo tempo e não se repete sob comando, então o
> BDD produz as duas ordens possíveis em uma thread só. Na primeira, a transação do `PaymentConfirmed` fica aberta, a
> varredura do prazo comita em transação própria, e o commit da primeira falha por conflito de versão e reverte tudo,
> inclusive a linha da inbox; a reentrega cai na F7 e o pagamento é estornado. Na segunda, a confirmação comita antes e a
> varredura não acha a saga. Isso prova o `@Version` e a convergência, **não** a corrida em si. O orçamento dos dois
> cenários é só de serviços: com peça, as duas transações disputam a mesma linha do estoque e o lock cruzado pode virar um
> deadlock de verdade, em que o Postgres derruba um lado e a reentrega faz os dois convergirem. **Hipótese, não medida.**

> **F4 provado em ambiente real (2026-10-29).** Uma cobrança criada e nunca paga — o cliente não abriu o
> link — teve o prazo estourado pelo *scheduler* do orquestrador, e a compensação correu até o fim do que
> depende de Billing: `cancel_reason = PAYMENT_EXPIRED` na saga, orçamento `CANCELED` e pagamento
> `CANCELED` com o mesmo motivo. É justamente o ramo em que o provedor **não tem API de cancelamento**
> (não existe id de pagamento a cancelar, porque ninguém pagou), e é por isso que o cancelamento é
> *best-effort* e o `PaymentCanceled` sai de qualquer forma — ver
> [ADR-008](adr/ADR-008-pagamento-mercado-pago.md). Esta prova não estava no plano do bloco: apareceu
> porque a sessão rodou tempo real com prazos reais.

> **F4 com recusa antes do prazo (G55, 2026-11-05).** "Recusas no meio só ficam registradas" nunca tinha rodado:
> 0 linhas de `no longer cancelable` e de `refused to cancel` em 30 dias. Com credencial real, cancelar o id da tentativa
> recusada responderia 2018, e o 2018 viraria estorno de algo nunca pago. O conserto (billing #10) cancela sem estorno
> quando o provedor diz `rejected` ou `cancelled` ([ADR-008](adr/ADR-008-pagamento-mercado-pago.md)). Na sandbox, o mesmo
> cancelamento responde 401 e cai na recusa comum, que já cancela: o `@F4` com o cartão `FUND` passa com ou sem o conserto,
> e a prova do conserto é o teste puro. A prova do cenário é a linha `PaymentAttemptRejected` antes do prazo, seguida da do
> ramo que rodou. **Medido no EKS em 2026-11-07** (OS 3, saga `beeca3d9-c61e-4dbf-a522-e32c2bef28db`, `@F4` verde):
> `PaymentAttemptRejected` (`cc_rejected_insufficient_amount`) às 00:27:52 UTC, antes do prazo das 00:29:25; vencido o
> prazo, o cancelamento do pagamento `188245865886` recebeu 401 (`refused to cancel … compensation continues`), o Billing
> publicou `PaymentCanceled` e a saga terminou `COMPENSATED` com `PAYMENT_EXPIRED`, com a DLQ do Billing em 0.

> **F8 provado em ambiente real, sem simulação (2026-10-29).** A sandbox de contas de teste do Mercado
> Pago recusa o estorno com `401 Unauthorized use of live credentials`, e a recusa exercitou a linha
> inteira: retentativa com a mesma chave de idempotência, retentativa esgotada, mensagem na DLQ e a saga
> **visível** em `COMPENSATING` com o passo de estorno pendente — não perdida, não silenciosamente
> abandonada. É a diferença que esta matriz existe para garantir: compensação é **retentável**, nunca
> desistível. O detalhe da recusa está no [ADR-008](adr/ADR-008-pagamento-mercado-pago.md).

## Execução no EKS

Medido em 2026-11-05, pelo Gateway real, com aprovação em `PT2M`, pagamento em `PT5M` e varredura de `30s`. Cada passo saiu no
log do GitHub Actions com o `correlationId` `EVIDENCIA-B7-<execução>-<linha do cenário>`.

| Execução do `e2e.yml` | Tags | Linhas da matriz | Cenários | Passos |
|---|---|---|---|---|
| [41943726930](https://github.com/CleytonOngaratto/fiap-fase4-os-service/actions/runs/41943726930) | `@F0a` | F0a | 1 | 6 |
| [41943933716](https://github.com/CleytonOngaratto/fiap-fase4-os-service/actions/runs/41943933716) | `@e2e and not @pagamento and not @infra` | F0a, F1, F2, F3, F4, F9b ×3, F9c, F12 | 10 | 60 |
| [41945434098](https://github.com/CleytonOngaratto/fiap-fase4-os-service/actions/runs/41945434098) | `@caminho-feliz` | Caminho feliz | 1 | 21 |
| [41946206096](https://github.com/CleytonOngaratto/fiap-fase4-os-service/actions/runs/41946206096) | `@F8` | F6, F8 | 1 | 8 |
| [41947002616](https://github.com/CleytonOngaratto/fiap-fase4-os-service/actions/runs/41947002616) | `@infra` | F9, F13 | 2 | 18 |

São 14 cenários distintos, todos verdes: o `@F0a` rodou sozinho primeiro e de novo na bateria. F0b, F5, F7, F10 e F11 ficam só
no componente, pelos motivos da coluna "Como provar".

**Estado final**, lido no banco de cada serviço e nas filas, depois das execuções:

| O que se mediu | Resultado |
|---|---|
| Sagas | 13 `COMPENSATED`, 1 `COMPLETED` e 1 `COMPENSATING` |
| A saga que ficou aberta | OS 13, `REPAIR_FAILED`, `REFUND_PAYMENT` pendente: a F6 termina no estado da F8, porque a sandbox recusa todo estorno |
| OS | 13 `CANCELED`, 1 `DELIVERED` e 1 `IN_PROGRESS` (a OS 13) |
| Outbox pendente | OS 0 de 54, Billing 0 de 30, Execution 0 de 43 |
| Comandos com `occurred_at` empatado na mesma saga | 0 |
| DLQs | `os-saga-replies` 0, `execution-commands` 0, `billing-commands` 1 (a mensagem da F6) |
| Caminho feliz | OS 12 `DELIVERED`, orçamento `APPROVED`, pagamento `CONFIRMED` no provedor com o id `187833409670`, sem injeção manual |
| F6 | OS 13, orçamento `CANCELED`, pagamento `CONFIRMED` com o id `186775499739`, item `REPAIR_FAILED`, estoque de volta a 10 |

O runbook foi exercitado nessa mensagem: [evidências do redrive](runbook-redrive-dlq.md#evidências).

## Invariantes que a matriz garante

1. **Nenhum dinheiro retido sem serviço:** todo caminho que cancela uma OS com pagamento confirmado passa por
   `RefundPayment` (F6, F7), e o estorno é retentado até confirmar (F8).
2. **Nenhuma peça presa:** toda reserva termina em baixa (pagamento confirmado) ou em liberação (F4, F5); toda
   baixa de uma OS cancelada termina em devolução (F6).
3. **`CANCELED` é verdade completa:** a OS só fica cancelada depois que **todas** as compensações foram
   confirmadas pelos participantes.
4. **Depois do pivô não se desfaz:** com o reparo concluído, falhas de notificação ou entrega só se retentam.
