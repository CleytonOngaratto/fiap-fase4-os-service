# ADR-009 — Uma tabela só no DynamoDB para o Execution Service, com a outbox dentro da transação

- **Status:** aceita
- **Data:** 2026-10-30
- **Contexto do bloco:** Execution Service (fila de diagnóstico e reparo por filial)

## Contexto

O Execution Service é o participante NoSQL da fase: o OS e o Billing têm Postgres próprio, e a divisão de
bancos reserva a ele o DynamoDB, porque o acesso dele é **por chave**, nunca por junção. Ele precisa de quatro
coisas ao mesmo tempo:

1. o **estado** do item de execução de cada ordem de serviço, com transições que não podem se atropelar;
2. o **histórico de etapas**, que é só acréscimo e é o que explica depois o que aconteceu;
3. uma **inbox**, para a reentrega do mesmo comando não produzir segundo efeito;
4. uma **outbox**, para "gravar o estado e publicar o evento" ser um ato só — a mesma invariante que o
   `saga_outbox` garante nos dois Postgres.

O ponto difícil é o 4 com o 1. Publicar no SNS dentro do mesmo passo que grava o estado não é atômico: se a
publicação falha depois da gravação, a saga perde a resposta; se a gravação falha depois da publicação, o
orquestrador avança sobre um estado que não existe. Foi exatamente a classe de erro que custou mais caro no
bloco do OS Service, e ali a solução foi a outbox na mesma transação do banco.

## Decisão

**Uma tabela única**, com chave de partição `pk = WO#<workOrderId>` e os quatro tipos de item distinguidos pela
chave de ordenação (`STATE`, `STEP#…`, `INBOX#…`, `OUTBOX#…`), e **toda escrita num `TransactWriteItems`** que
inclui o item de outbox. O relay publica depois, lendo a outbox por um índice esparso.

Dois índices secundários globais:

- **`gsi-branch-status`** (`<branchId>#<status>` / `enqueuedAt`) — a fila do mecânico por filial, na ordem de
  espera, em uma consulta.
- **`gsi-outbox-pending`** (`PENDING` / `occurredAt`) — as pendências de **todas** as ordens, que o relay
  precisa varrer sem conhecer as partições.

Concorrência por **escrita condicional no status e na versão** lidos, com leitura forte. A inbox condiciona em
`attribute_not_exists` na própria chave.

## Alternativas descartadas

**Uma tabela por agregado** (estado, histórico, inbox, outbox separadas). É o desenho que um olhar relacional
sugere, e foi descartado pelo motivo que mais importa aqui: o `TransactWriteItems` do DynamoDB atravessa
tabelas, mas o custo e o limite de itens crescem, e nada disso compra isolamento — as quatro escritas são
**sempre** da mesma ordem de serviço. Com a partição comum, a transação é local a uma partição, que é o caso
que o DynamoDB faz melhor. A tabela única também torna o histórico de uma OS uma consulta só.

**Publicar direto no SNS, sem outbox.** Era o que o esqueleto fazia, e funcionava porque o esqueleto só
publicava um evento automático, sem estado para gravar junto. Com estado, a publicação direta reintroduz a
janela entre gravar e publicar.

**`Update` de campos em vez de `Put` do item inteiro.** Descartado por experiência medida no bloco do OS
Service: três defeitos vieram de gravação que copiava campo a campo e não foi estendida quando o agregado
ganhou campo novo — o domínio calculava certo, o log dizia que havia gravado, e o banco guardava a versão
velha. O `Put` do item inteiro torna esse esquecimento impossível.

## Consequências

**Positivas**

- "Ou grava tudo e publica, ou nada acontece" vale também no participante NoSQL, sem transação distribuída.
- A fila por filial é uma consulta de índice, sem varredura e sem filtro.
- A idempotência do comando é uma condição de escrita, não uma leitura seguida de escrita: a reentrega não
  produz segundo efeito **nem segunda resposta**.
- Campo novo do agregado entra na gravação por construção.

**Negativas (aceitas e mitigadas)**

- **Não existe `SKIP LOCKED`.** Com mais de uma réplica, as duas leem o mesmo lote de pendências e a mensagem
  pode sair duas vezes. Mitigação: a condição `attribute_not_exists(sentAt)` impede a marca dupla, e a garantia
  durável contra a entrega dupla é a **inbox do orquestrador**, que deduplica por `messageId` — razão pela qual
  o `messageId` do evento de resposta é derivado do comando, e não aleatório. A janela de deduplicação do SNS
  FIFO ajuda, mas não basta: ela expira em 5 minutos.
- **Partição única no índice de pendências.** Adequada ao volume do projeto, concentra escrita numa partição
  numa carga real. Registrado em "Lab × Produção": a chave passaria a ser fragmentada.
- **Modelo menos legível para quem vem do relacional.** Mitigação: o desenho das chaves está em
  [banco-de-dados.md](../banco-de-dados.md) e os nomes dos atributos vivem num único lugar no código.
- **O teste de integração cria a tabela por código.** Se ele e o `terraform/dynamodb.tf` divergirem, o teste
  passa contra um esquema que a nuvem não tem. Mitigação: o recurso de teste traz um comentário dizendo isso, e
  a sessão na AWS exercita a tabela de verdade.

## Evidências

| Data | Evidência |
|---|---|
| 2026-09-29 | Spike na conta do laboratório: `TransactWriteItems` com estado + outbox, e a transação com condição falsa **cancelada inteira** — o item de outbox dela não existe |
| 2026-10-30 | `./mvnw clean verify` do Execution com 137 testes verdes e cobertura de linha 99,2%; *round-trip* do item `STATE` tocando os 14 campos |
| 2026-10-30 | Teste de integração com DynamoDB Local e ElasticMQ: a mesma mensagem duas vezes deixa 1 item de estado, 1 de inbox e **1** de outbox; `CancelDiagnosis` sem item nenhum escreve a resposta; a fila por filial volta ordenada por `enqueuedAt` |
