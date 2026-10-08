# ADR — Saga orquestrada, com o orquestrador em código no OS Service

> Escrito no B0 (2026-10-01), revisado pelo dono em 2026-10-05.

- **Status:** Aceita (2026-09-29)
- **Decisores:** dono do projeto, com análise e medições do agente
- **Relacionados:** [máquina de estados v2](../maquina-de-estados.md) · [catálogo v1](../catalogo-de-mensagens.md) ·
  [matriz de compensação](../matriz-de-compensacao.md)

## Contexto

A oficina passou a operar com três serviços independentes — OS, Billing (orçamento e pagamento) e Execution
(diagnóstico e reparo) — cada um com banco próprio. O processo de uma OS atravessa os três e **não cabe numa
transação**: não existe transação distribuída entre Postgres, DynamoDB, SQS e Mercado Pago, e um 2PC acoplaria
a disponibilidade de todos. O edital exige o **Saga Pattern com rollback e compensação em qualquer etapa**,
orquestrado ou coreografado, com a escolha justificada.

Forças que pesaram na escolha:

1. **Espera humana longa** — o cliente aprova o orçamento em horas; o mecânico diagnostica quando pode.
2. **Evento externo** — o pagamento é confirmado por webhook do Mercado Pago.
3. **Compensações com ordem** — estornar antes de devolver peças, liberar reserva antes de cancelar o
   orçamento, e só então cancelar a OS.
4. **Prazos** — aprovação e pagamento expiram e disparam compensação.
5. **Testabilidade obrigatória** — pelo menos um fluxo completo em BDD e cobertura ≥ 80% por serviço, rodando no
   pull request **sem** depender do ambiente de nuvem (que é efêmero no AWS Academy).
6. **Rastreabilidade** — responder "em que passo está a OS 42 e o que falta desfazer?" sem caçar logs em três
   serviços.

## Decisão

**Saga orquestrada.** O orquestrador vive **no OS Service**, em código, com o estado de cada saga persistido no
Postgres do próprio OS (`saga_instance`, com versão otimista e prazos). Ele envia **comandos** às filas SQS FIFO
dos participantes e reage aos **eventos** que eles publicam em tópicos SNS FIFO. Os passos que são do próprio
OS (precificar, reservar, baixar e devolver peças) acontecem na **mesma transação local** que avança a saga e
grava o próximo comando no **outbox**.

## Alternativas consideradas

### AWS Step Functions (orquestração gerenciada) — viável, descartada por escolha

**Provada viável no AWS Academy em 2026-09-29:** a `LabRole` confia em `states.amazonaws.com`, e uma state
machine real rodou o padrão `.waitForTaskToken` via SQS nos dois caminhos — `SendTaskSuccess` →
`SUCCEEDED`, e `SendTaskFailure(PaymentFailed)` → `Catch` → estado de compensação → `CANCELED`.

| A favor | Contra (o que decidiu) |
|---|---|
| Máquina de estados visual, ótima para demonstrar | O **BDD do fluxo completo** precisaria de um emulador de Step Functions (o LocalStack passou a exigir conta e token em mar/2026) ou rodaria contra a AWS — **fora do PR** |
| Espera durável nativa (até 1 ano) com `waitForTaskToken` | Definição em ASL no Terraform, fora do código e da cobertura do serviço |
| `Retry`/`Catch` e histórico de execução prontos | O orquestrador não pertenceria a nenhum serviço: ficaria entre os repositórios, sem dono claro |
| | Acoplamento ao provedor (*lock-in*) numa peça central do domínio |

### Coreografia por eventos (SNS/SQS) — descartada

| A favor | Contra (o que decidiu) |
|---|---|
| A opção mais desacoplada, sem ponto central | O fluxo fica **implícito**, espalhado em três repositórios |
| Simples com poucos serviços e fluxo linear — e funciona igualmente sobre SNS/SQS, sem precisar de Kafka | **Compensações com ordem** ficariam distribuídas: cada serviço teria de saber quem desfazer e quando |
| | Prazos sem dono natural (quem expira a aprovação?) |
| | "Em que passo está a OS?" exige uma projeção extra, montada a partir de todos os eventos |
| | O BDD do fluxo completo teria de subir os três serviços |

## Consequências

**Positivas**

- O processo inteiro está **num lugar só**, legível como uma máquina de estados e testável por inteiro no PR
  (BDD de componente com os participantes simulados).
- Prazos e compensações têm **dono**: o *scheduler* do orquestrador expira e manda compensar, na ordem certa.
- O estado da saga responde "onde está e o que falta desfazer" — base para dashboards e para o alerta de saga
  parada.
- Os participantes ficam simples: executam comandos idempotentes e publicam fatos, sem conhecer o processo.

**Negativas (aceitas e mitigadas)**

- **Nós escrevemos e mantemos a máquina de estados.** Mitigação: pacote `saga` isolado, com regra própria no
  ArchUnit, e a matriz de compensação inteira como cenários de BDD.
- **O OS Service acumula responsabilidade** (domínio da OS + orquestração). Mitigação: fronteira de pacote; a
  orquestração não acessa os bancos dos outros, só manda comandos.
- **O OS vira dependência do processo:** se ele cair, as sagas param (as mensagens esperam nas filas, sem
  perda). Mitigação: réplicas + HPA, filas com DLQ e alerta de saga parada.
- **Isolamento perdido** (o "I" do ACID) entre etapas. Mitigação: *semantic lock* (reserva de peças, estados
  `PENDING_*`), versão otimista, ordem por OS na FIFO e passo pivô bem definido.

## Em produção, o que poderia mudar

Com mais serviços, sagas mais longas ou volume alto, valeria trocar a máquina de estados própria por um **motor
de workflow durável** (Step Functions, ou uma solução de workflow como código), mantendo a mesma separação
comando/evento e as mesmas compensações. A decisão de **orquestrar** continua válida; o que mudaria é **quem
executa** o orquestrador.

## Evidências

| Data | Evidência |
|---|---|
| 2026-09-29 | Step Functions com a `LabRole`: sucesso e falha → compensação provados (alternativa viável, descartada por escolha) |
| 2026-09-29 | SNS FIFO → SQS FIFO: ordem por OS, duplicado descartado, *message attributes* intactos, DLQ após `maxReceiveCount` |
| 2026-09-29 | DynamoDB `TransactWriteItems`: estado + outbox, cancelamento integral quando a condição falha |
