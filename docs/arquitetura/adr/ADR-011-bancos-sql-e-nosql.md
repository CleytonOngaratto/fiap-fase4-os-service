# ADR-011 — Bancos: PostgreSQL próprio no OS e no Billing, DynamoDB no Execution

> Escrito em 2026-11-09, com a decisão de 2026-09-24 e as medições das sessões no EKS.

- **Status:** Aceita (2026-09-24) · os três bancos rodaram juntos no EKS em 2026-10-31
- **Decisores:** dono do projeto, com análise e medições do assistente de IA
- **Relacionados:** [banco-de-dados.md](../banco-de-dados.md) · [ADR-009 — tabela única do Execution](ADR-009-tabela-unica-do-execution.md) ·
  [ADR-005 — divisão em serviços](ADR-005-divisao-em-servicos.md) · [ADR-002 — credencial dos pods](ADR-002-credencial-dos-pods.md)

## Contexto

O enunciado exige banco próprio por serviço, ao menos um SQL e um NoSQL, e destaca que nenhum serviço acessa o banco de
outro. Na fase anterior havia um PostgreSQL só, compartilhado pelos módulos do monolito.

A escolha parte de como cada serviço usa o próprio dado:

| Serviço | O que guarda | Como acessa |
|---|---|---|
| **OS** | ordens de serviço, cadastros, catálogo, estoque com reserva e o estado da Saga | relações entre tabelas, e uma transação local que avança a Saga e grava o outbox |
| **Billing** | orçamento, cobrança e tentativas recusadas | dinheiro, com regras de unicidade que sustentam a idempotência |
| **Execution** | fila de diagnóstico e reparo por filial e histórico de etapas | sempre por ordem de serviço, ou por filial e status; nenhuma junção |

## Decisão

**OS Service e Billing Service em RDS PostgreSQL, cada um com a sua instância. Execution Service em Amazon DynamoDB.**

- **SQL onde há dinheiro e relações.** O passo da Saga e o outbox entram numa só transação ACID, o relay drena as pendências
  com `FOR UPDATE SKIP LOCKED`, e as constraints recusam o segundo orçamento da mesma ordem e a notificação repetida do
  provedor.
- **NoSQL onde o acesso é por chave.** A fila do mecânico é uma consulta de índice, e o histórico de uma ordem é uma consulta
  de partição. O `TransactWriteItems` grava estado, histórico, inbox e outbox num ato só
  ([ADR-009](ADR-009-tabela-unica-do-execution.md)).
- **Duas instâncias RDS, não dois databases na mesma instância.** Assim cada serviço tem credencial, CPU, armazenamento e
  ciclo de vida próprios: o isolamento é físico, e "banco próprio" não fica sujeito a interpretação.
- **O Secret do banco só existe no namespace do próprio serviço**, gerado no deploy a partir do Parameter Store.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| **Dois databases numa instância RDS** | Mais barata, mas os dois serviços dividiriam instância, CPU e janela de manutenção: o isolamento seria só lógico, e o requisito de banco próprio ficaria contestável |
| **PostgreSQL nos três serviços** | Não atende "ao menos um NoSQL", e o Execution não faz junção nenhuma |
| **DynamoDB nos três serviços** | Perderia as constraints de unicidade e a transação local com `SKIP LOCKED`, que são o que mantém a Saga e a cobrança corretas com poucas linhas de código |

## Consequências

**Positivas**

- Cada serviço escolhe o modelo que combina com o próprio acesso, sem nenhum banco compartilhado.
- Consistência forte onde há dinheiro, e custo por uso onde o acesso é por chave: a tabela é `PAY_PER_REQUEST`.
- O relacional é testado contra PostgreSQL real e o NoSQL contra DynamoDB Local, ambos em contêiner, no build.

**Negativas (aceitas e mitigadas)**

- **Dois modelos de concorrência para manter:** `SKIP LOCKED` no Postgres e escrita condicional no DynamoDB. Mitigação: cada
  um documentado em [banco-de-dados.md](../banco-de-dados.md), e o relay do Execution se apoia na inbox do orquestrador.
- **Custo da segunda instância:** cerca de US$ 0,017 por hora (`db.t3.micro` em `us-east-1`), só com o ambiente de pé.
- **Ressalva do laboratório:** com a `LabRole` em todos os nós, o isolamento das credenciais é por namespace e por convenção,
  não por IAM ([ADR-002](ADR-002-credencial-dos-pods.md)).

## Em produção, o que poderia mudar

Multi-AZ, backups com recuperação a um ponto no tempo, réplicas de leitura e Performance Insights no RDS, backup contínuo no
DynamoDB e uma role por serviço, com acesso só aos próprios dados. A divisão continuaria a mesma. O quadro completo está em
[Lab × Produção](../lab-vs-producao.md).

## Evidências

| Data | Evidência |
|---|---|
| 2026-09-29 | Spike na conta do laboratório: `TransactWriteItems` com estado e outbox, e a transação com condição falsa cancelada inteira ([ADR-009](ADR-009-tabela-unica-do-execution.md)) |
| 2026-10-14 | Os três serviços no EKS, cada um no seu namespace, com os segredos de cada um visíveis só no namespace dele ([ADR-005](ADR-005-divisao-em-servicos.md)) |
| 2026-10-24 | Sessão no EKS: o RDS do OS em PostgreSQL 16.13, com as migrations aplicadas pelo Flyway até a V8.0.0 |
| 2026-10-25 | Billing: PostgreSQL 16 real, em contêiner, recusa o segundo orçamento da mesma ordem e a notificação repetida ([banco-de-dados.md](../banco-de-dados.md#evidências)) |
| 2026-10-31 | Execution no EKS, na tabela real: fila por filial na ordem de espera e mensagem duplicada absorvida pela inbox, com 2 inbox, 1 outbox e 2 passos |
| 2026-11-09 | Código lido: [`terraform/rds.tf` do OS](../../../terraform/rds.tf) e [do Billing](https://github.com/CleytonOngaratto/fiap-fase4-billing-service/blob/main/terraform/rds.tf), cada um com a própria instância PostgreSQL 16 `db.t3.micro`, `gp2` e `multi_az = false`; [`terraform/dynamodb.tf` do Execution](https://github.com/CleytonOngaratto/fiap-fase4-execution-service/blob/main/terraform/dynamodb.tf), `PAY_PER_REQUEST`, com 2 índices e TTL |
