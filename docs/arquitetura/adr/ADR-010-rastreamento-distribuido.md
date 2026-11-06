# ADR-010 — Rastreamento distribuído pela mensageria, com o contexto guardado no outbox

- **Status:** aceita (a travessia pelo SNS ainda será medida no EKS)
- **Data:** 2026-11-06
- **Contexto do bloco:** B8, observabilidade distribuída (decisão G53)

## Contexto

O edital pede que se siga uma ordem de serviço pelos três serviços. No fim do B7, o New Relic não conseguia:

- **0 de 72 traços** com mensagens e chamadas web passavam de um serviço. Pelo HTTP o traço já atravessava, no
  Execution chamando o catálogo do OS. Pela mensageria, não.
- O Billing não enviava `correlationId` nem `sagaId` ao New Relic, e a linha de erro dos consumidores era escrita
  **depois** de o contexto do log ser fechado.
- O agente abria uma transação `.../AroundInvokeInvocationContext/proceed` a cada ciclo dos relays: **11.295 em
  24 h** no OS e **11.283** no Billing, quase todas de ciclos vazios.

O traço não atravessava por dois motivos. O agente só injeta cabeçalhos de traço numa chamada feita **dentro** de uma
transação. Além disso, quem publica é o relay, um laço agendado que não tem nada a ver com o passo de negócio que
gravou o evento. Mesmo que o relay injetasse cabeçalhos, o pai do traço seria o relay, e não a requisição que abriu
a OS.

## Decisão

**O contexto de traço é capturado no outbox, na mesma transação do passo, e o relay só o copia.**

1. **Gravar.** O gateway do outbox pede o contexto da transação atual ao agente e o guarda com o evento: coluna
   `trace_context` (`text`) no OS e no Billing, atributo `traceContext` no item `OUTBOX#` do Execution. É `text`,
   não `varchar`, porque estourar o tamanho reverteria o passo da saga. Nada muda no orquestrador (G8).
2. **Enviar.** O relay copia os 3 cabeçalhos (`newrelic`, `traceparent`, `tracestate`) para os atributos da
   mensagem. Só vai atributo com valor, e contexto corrompido sai sem atributo. O agente não sobrescreve um atributo
   que já existe, então o contexto guardado vence o do relay.
3. **Receber.** Cada mensagem consumida vira uma transação própria (`@Trace(dispatcher = true)` no `consume`). Antes
   de qualquer outra coisa, ela aceita o contexto como transporte de fila e ganha o nome `Message/<fila>/<tipo>`,
   mais os atributos `sagaId`, `workOrderId`, `messageType` e `branchId`. Falha tratada chama `noticeError`: sem
   isso, o erro só aparecia no log, nunca em `TransactionError`.
4. **Ruído.** Os relays do OS e do Billing dão à transação do interceptador o nome `Scheduled/<identity>` e a
   ignoram quando o ciclo está vazio. A varredura de prazos se chama `Scheduled/saga-deadline-scan`. O relay do
   Execution abre uma transação por mensagem, que aceita o contexto guardado, e nenhuma por ciclo.

A API do agente fica atrás da interface `TraceContext`, em `infrastructure/observability` de cada serviço, e o
ArchUnit segue proibindo domínio e caso de uso de conhecerem o New Relic. O `newrelic-api` entra em `compile`, na
mesma versão do agente (9.4.0). Sem o agente, a API é inerte. Com `provided`, a aplicação não sobe sem ele.

**Telemetria nunca derruba mensagem.** `NewRelicTraceContext` não lança, e cada ponto tem teste puro:

| Ponto | Se a API do agente falhar |
|---|---|
| Captura no outbox (`MANDATORY`, dentro do passo) | a linha é gravada sem contexto; o passo não reverte |
| Relay | a mensagem sai com os atributos do envelope e sem os de traço |
| Consumo | o handler roda e a mensagem é apagada como sempre |

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| **Só o `sagaId`, sem traço** | Continua sendo o fio contínuo e a garantia, porque cada passo humano abre um traço novo. Mas não mostra dependência nem latência entre serviços, que é o que o traço dá. |
| **Deixar o agente injetar no envio** | O pai seria o relay, não o passo de negócio, e o relay do OS e do Billing processa um lote numa transação só. |
| **Desligar o módulo `ejb-4.0` do agente** para acabar com os `proceed` | **Reprovado no spike.** Sem ele, a transação `RestWebService/work-orders` some, porque é o interceptador do `@Transactional` que a abre na thread de trabalho, e o outbox gravou o contexto **nulo**. O `http.statusCode` seguiu nas transações web, mas sem o contexto a cadeia não existe. |
| **OpenTelemetry** | Seria um segundo agente ao lado do New Relic, que já está em todas as imagens, painéis e alertas. |

## Consequências

- **Cada passo humano abre um traço novo**: abrir a OS, concluir o diagnóstico, aprovar e pagar. O traço com os
  3 serviços é o do diagnóstico concluído: Execution → `DiagnosisCompleted` no OS → `CreateBudget` no Billing.
  Os traços da mesma OS se ligam pelo `sagaId`.
- O relay do OS e do Billing aparece como transação própria, sem pai. O consumidor do outro lado é filho do passo
  que gravou o evento, e não do relay.
- A mensagem usa 7 dos 10 atributos do SNS e do SQS ([ADR-006](ADR-006-mensageria.md)).
- O `correlationId` não é contínuo: depois do webhook do Mercado Pago e nos passos disparados pelo prazo ou pelo
  cancelamento, o comando sai sem ele, porque não há requisição de origem. O fio contínuo é o `sagaId`.
- O módulo `ejb-4.0` segue ligado. Os `proceed` que sobram são do boot.

## Em produção, o que poderia mudar

Com volume real, a amostragem do traço distribuído passaria a importar, porque um traço por mensagem custa
ingestão. A decisão de guardar o contexto no outbox não mudaria: ela vale para qualquer ferramenta que use
W3C Trace Context.

## Evidências

| Data | Evidência |
|---|---|
| 2026-11-05 | Spike local (Docker, agente real, apps `*-local`, ElasticMQ). Traço `95c016fe7a3457d380705dbd362b46e9`: `RestWebService/work-orders (POST)` no OS → `Message/execution-commands/StartDiagnosis` no Execution, `entityCount = 2`, raiz no OS. A mensagem saiu com **7** atributos, e o `traceparent` no fio era o guardado no outbox. A linha de log dentro do consumo tem `trace.id`. |
| 2026-11-05 | Mesmo spike com `ejb-4.0` desligado: sem `RestWebService/work-orders` e com `trace_context` nulo no outbox da OS 2. Alternativa reprovada. |
| 2026-11-06 | Imagens com o código final, `ejb-4.0` ligado. Traço `3632c28b1243a9d3d659fc4a57c6faf1`: OS → `StartDiagnosis`. Traço `db69d66d1d6df027e82a49479874f063`: `diagnosis/start` → `Scheduled/execution-outbox-relay`, o relay entrando no traço de quem gravou. Entre 16:35 e 16:41 UTC, `Scheduled/saga-outbox-relay` 1 vez (só o ciclo que enviou), `Scheduled/saga-deadline-scan` 12 vezes, e os `proceed` restantes todos do boot. Resposta para OS inexistente: 3 linhas em `TransactionError`, uma por recebimento, com o nome da transação. |
| *pendente* | A travessia pelo SNS (Execution e Billing → OS), que não tem emulador local: traço do diagnóstico concluído com os 3 serviços, na sessão do B8. |
