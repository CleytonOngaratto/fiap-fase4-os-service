# Documentação arquitetural — Fase 4 (microsserviços com Saga)

Evolução da Car Workshop API (Fase 3, um monolito no EKS) para **três microsserviços independentes** — OS,
Billing e Execution —, cada um com repositório, infraestrutura e banco próprios, coordenados por uma **Saga
orquestrada** sobre SNS/SQS FIFO, com pagamento pelo Mercado Pago, na AWS.

A documentação da Fase 3 continua válida como **estado anterior** e não foi copiada para cá:
[fiap-fase3-app/docs/arquitetura](https://github.com/CleytonOngaratto/fiap-fase3-app/tree/main/docs/arquitetura)
(nuvem, banco, autenticação por CPF e os ADRs daquela fase). Os documentos abaixo descrevem o que muda.

## Documentos

| Documento | O que responde |
|---|---|
| [maquina-de-estados.md](maquina-de-estados.md) | As três máquinas de estado e quem é dono de cada uma: status da OS (o que o cliente vê), estado da saga (o orquestrador) e os estados locais do Billing e do Execution; o que mudou em relação à Fase 3 |
| [catalogo-de-mensagens.md](catalogo-de-mensagens.md) | Topologia SNS/SQS FIFO, envelope padrão (`messageId`, `sagaId`, `correlationId`…), comandos e eventos com dono, consumidores e versão, passos locais do orquestrador e as chamadas REST síncronas que existem, com o porquê |
| [matriz-de-compensacao.md](matriz-de-compensacao.md) | Caminho feliz etapa por etapa e as falhas F0a–F13: quem detecta, as compensações **na ordem**, o estado final em cada serviço e como cada linha é provada |
| [adr/ADR-001-saga-orquestrada.md](adr/ADR-001-saga-orquestrada.md) | Por que Saga **orquestrada**, com o orquestrador em código no OS Service — e por que não Step Functions nem coreografia |
| [adr/ADR-002-credencial-dos-pods.md](adr/ADR-002-credencial-dos-pods.md) | Como os pods obtêm credencial AWS sem nenhum segredo (`LabRole` no node group + IMDSv2 com *hop limit* 2), por que IRSA e Pod Identity ficaram de fora no AWS Academy e o custo disso em isolamento |
| [adr/ADR-003-borda-privada.md](adr/ADR-003-borda-privada.md) | Como o tráfego entra sem nenhum load balancer público (API Gateway → VPC Link → ALB interno → pods), quem é dono de cada peça e por que a allowlist de rotas do Gateway não basta sozinha |
| [adr/ADR-004-gateway-persistente.md](adr/ADR-004-gateway-persistente.md) | Por que o API Gateway vive fora do ciclo de sessão (URL estável para o webhook do Mercado Pago), como os roots `persistent/` e `session/` dividem a borda e o que um *Reset* do laboratório apaga |
| [componentes.md](componentes.md) | Diagrama geral: os três serviços, seus bancos, a mensageria, a borda, o Mercado Pago e a observabilidade; quem é dono de cada dado e por que cada travessia de fronteira é síncrona ou assíncrona |
| [adr/ADR-005-divisao-em-servicos.md](adr/ADR-005-divisao-em-servicos.md) | Por onde o monolito foi cortado em três serviços, e por que não por camada nem por entidade |
| [banco-de-dados.md](banco-de-dados.md) | Os três bancos: o Postgres do OS (estado de negócio e de processo, snapshot precificado, histórico, outbox, inbox, `saga_instance`, estoque com reserva), o Postgres do Billing (orçamento, cobrança, tentativas recusadas e as constraints que sustentam a idempotência) e a tabela única do DynamoDB do Execution |
| [adr/ADR-011-bancos-sql-e-nosql.md](adr/ADR-011-bancos-sql-e-nosql.md) | Por que PostgreSQL próprio no OS e no Billing e DynamoDB no Execution, e por que duas instâncias RDS em vez de dois databases numa só |
| [adr/ADR-007-lambda-sem-banco-do-os.md](adr/ADR-007-lambda-sem-banco-do-os.md) | Por que a Lambda de autenticação consulta um endpoint interno do OS Service em vez de ler a tabela de clientes, e por que a allowlist do Gateway não basta sozinha |
| [adr/ADR-006-mensageria.md](adr/ADR-006-mensageria.md) | Por que SNS FIFO publicando em SQS FIFO com outbox transacional, o que isso custa (bloqueio de cabeça de fila, idempotência obrigatória) e o que ficou como dívida registrada |
| [adr/ADR-008-pagamento-mercado-pago.md](adr/ADR-008-pagamento-mercado-pago.md) | Por que **Checkout Pro** e não Checkout API nem Orders API; as quatro chamadas ao provedor; por que o status vem sempre do `GET /v1/payments/{id}`; a chave de idempotência do estorno; o cancelamento *best-effort* e as duas camadas do pagamento que chega depois do cancelamento |
| [adr/ADR-010-rastreamento-distribuido.md](adr/ADR-010-rastreamento-distribuido.md) | Como o traço distribuído atravessa a mensageria: o contexto guardado no outbox na transação do passo, o relay só copiando, uma transação por mensagem consumida; por que desligar o módulo `ejb-4.0` do agente foi reprovado no spike |
| [adr/ADR-009-tabela-unica-do-execution.md](adr/ADR-009-tabela-unica-do-execution.md) | Por que **uma tabela só** no DynamoDB do Execution, com estado, histórico, inbox e outbox na mesma partição da ordem de serviço; por que a outbox entra no `TransactWriteItems` em vez de publicar direto; os dois índices secundários; e o que fazer sem `SKIP LOCKED` |
| [sequencia.md](sequencia.md) | Sequência da Saga: caminho feliz, compensação depois do pagamento e cancelamento pelo atendimento; e o que a "ordem" das compensações quer dizer de verdade |
| [runbook-redrive-dlq.md](runbook-redrive-dlq.md) | O que fazer quando uma mensagem para numa DLQ: identificar, classificar, reenviar e verificar, e por que o reenvio é seguro |
| [lab-vs-producao.md](lab-vs-producao.md) | O que mudaria numa conta AWS real, decisão por decisão, e por que cada escolha é a melhor dentro das restrições do laboratório |

## Onde está cada tema pedido no Tech Challenge

| Tema do enunciado | Documento |
|---|---|
| Três ou mais microsserviços independentes, cada um com repositório, infraestrutura e banco próprios | [componentes.md](componentes.md) · [ADR-005](adr/ADR-005-divisao-em-servicos.md) · [Repositórios](#repositórios) |
| OS Service: abertura, atualização de status, consulta de status e histórico | [maquina-de-estados.md §1](maquina-de-estados.md) · [banco-de-dados.md](banco-de-dados.md) (`work_order_status_history`) |
| Billing: orçamento para aprovação, pagamentos e atualização da OS após o pagamento | [maquina-de-estados.md §3](maquina-de-estados.md) · [catalogo-de-mensagens.md §3–4](catalogo-de-mensagens.md) · [banco-de-dados.md](banco-de-dados.md) |
| Execution: fila de execução, status durante diagnóstico e reparo, finalização comunicada ao OS | [maquina-de-estados.md §3](maquina-de-estados.md) · [catalogo-de-mensagens.md §4](catalogo-de-mensagens.md) · [ADR-009](adr/ADR-009-tabela-unica-do-execution.md) (a fila por filial no índice) |
| Integração com o Mercado Pago | [ADR-008](adr/ADR-008-pagamento-mercado-pago.md) · [catalogo-de-mensagens.md](catalogo-de-mensagens.md) (cobrança, confirmação e estorno) · [matriz-de-compensacao.md](matriz-de-compensacao.md) (F4–F8) |
| Banco próprio por serviço, ao menos um SQL e um NoSQL | [ADR-011](adr/ADR-011-bancos-sql-e-nosql.md) · [banco-de-dados.md](banco-de-dados.md) (Postgres do OS e do Billing; DynamoDB do Execution) · [ADR-009](adr/ADR-009-tabela-unica-do-execution.md) |
| Nenhum serviço acessa o banco de outro | [catalogo-de-mensagens.md §6](catalogo-de-mensagens.md) (o que é síncrono e por quê) · [ADR-002](adr/ADR-002-credencial-dos-pods.md) (isolamento de credenciais no laboratório) · [lab-vs-producao.md](lab-vs-producao.md) · [componentes.md](componentes.md) |
| Comunicação REST síncrona quando necessário e mensageria assíncrona | [catalogo-de-mensagens.md §1 e §6](catalogo-de-mensagens.md) · [ADR-006](adr/ADR-006-mensageria.md) |
| Saga com rollback e compensação em qualquer etapa | [matriz-de-compensacao.md](matriz-de-compensacao.md) · [maquina-de-estados.md §2](maquina-de-estados.md) |
| Saga orquestrada ou coreografada, com a escolha justificada no README | [README do OS](../../README.md#saga-estratégia-escolhida-e-por-quê) · [ADR-001](adr/ADR-001-saga-orquestrada.md) |
| Diagrama geral e sequência da Saga | [componentes.md](componentes.md) · [sequencia.md](sequencia.md) |
| Rastreamento dos fluxos distribuídos | [ADR-010](adr/ADR-010-rastreamento-distribuido.md) (traço pela mensageria) · [catalogo-de-mensagens.md §2](catalogo-de-mensagens.md) (`correlationId` e `sagaId` em toda mensagem) · [componentes.md](componentes.md#observabilidade) (métricas, painel e alertas) |
| Observabilidade da fase anterior reaproveitada | [componentes.md](componentes.md#observabilidade) · [README do OS](../../README.md#observabilidade) (painel e alertas) · [README da plataforma](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s) (`nri-bundle`) |
| Testes unitários em todos os microsserviços | Seção "Testes" de cada README: [OS](../../README.md#testes) · [Billing](https://github.com/CleytonOngaratto/fiap-fase4-billing-service#testes) · [Execution](https://github.com/CleytonOngaratto/fiap-fase4-execution-service#testes) |
| Ao menos um fluxo completo testado com BDD | [README do OS](../../README.md#testes) · [features](../../src/test/resources/features/saga) · [Execução no EKS](matriz-de-compensacao.md#execução-no-eks) |
| Cobertura mínima de 80% por serviço e SonarQube ou similar no CI | Seção "Evidência de cobertura" de cada README: [OS](../../README.md#evidência-de-cobertura) · [Billing](https://github.com/CleytonOngaratto/fiap-fase4-billing-service#evidência-de-cobertura) · [Execution](https://github.com/CleytonOngaratto/fiap-fase4-execution-service#evidência-de-cobertura) |
| Pipeline independente por serviço: build, testes, qualidade e deploy automatizado em Kubernetes | Seções "Deploy" e "Kubernetes" de cada README: [OS](../../README.md#deploy) · [Billing](https://github.com/CleytonOngaratto/fiap-fase4-billing-service#deploy) · [Execution](https://github.com/CleytonOngaratto/fiap-fase4-execution-service#deploy) |
| Repositórios protegidos: `main` com PR obrigatório e checagens automáticas | [Repositórios](#repositórios) |
| Cada repositório com código, Dockerfile, manifestos, pipeline, evidência de cobertura, documentação do serviço e Swagger/Postman | README de cada serviço e a pasta `docs/api/`: [OS](../../README.md#api) · [Billing](https://github.com/CleytonOngaratto/fiap-fase4-billing-service#api) · [Execution](https://github.com/CleytonOngaratto/fiap-fase4-execution-service#api) |
| Múltiplas filiais | [`branch/`](../../src/main/java/br/com/fiap/postech/carworkshop/branch) (filiais no OS) · [ADR-009](adr/ADR-009-tabela-unica-do-execution.md) (fila por filial) · [catalogo-de-mensagens.md §2](catalogo-de-mensagens.md) (`branchId` no envelope) · [componentes.md](componentes.md#observabilidade) (filtro por filial no painel) |

## Ordem de leitura sugerida

1. [ADR-001](adr/ADR-001-saga-orquestrada.md) — a decisão central e o porquê.
2. [maquina-de-estados.md](maquina-de-estados.md) — os estados que a saga move.
3. [catalogo-de-mensagens.md](catalogo-de-mensagens.md) — as mensagens que movem esses estados.
4. [matriz-de-compensacao.md](matriz-de-compensacao.md) — o que acontece quando cada etapa falha.
5. [sequencia.md](sequencia.md) — os mesmos fluxos no tempo, entre os serviços.
6. [banco-de-dados.md](banco-de-dados.md) — onde cada estado mora, nos dois Postgres e no DynamoDB.
7. [lab-vs-producao.md](lab-vs-producao.md) — o que mudaria numa conta AWS real.

## Repositórios

| Repositório | Conteúdo | Publica no SSM | Checks obrigatórios na `main` |
|---|---|---|---|
| [fiap-fase4-infra-k8s](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s) | Plataforma: VPC, EKS, ECR, ALB interno e tópicos SNS (Terraform) | `/fase4/vpc/*`, `/fase4/eks/*`, `/fase4/ecr/*`, `/fase4/sns/*`, `/fase4/alb/*` | `fmt-validate` |
| [fiap-fase4-os-service](https://github.com/CleytonOngaratto/fiap-fase4-os-service) | OS, cadastros, filiais, autenticação do ADMIN e o orquestrador da Saga (Quarkus) — este repositório | `/fase4/os/*` | `build`, `bdd`, `fmt-validate`, `SonarCloud Code Analysis` |
| [fiap-fase4-billing-service](https://github.com/CleytonOngaratto/fiap-fase4-billing-service) | Orçamento, aprovação pelo cliente e pagamento com Mercado Pago (Quarkus) | `/fase4/billing/*` | `build`, `fmt-validate`, `SonarCloud Code Analysis` |
| [fiap-fase4-execution-service](https://github.com/CleytonOngaratto/fiap-fase4-execution-service) | Fila de diagnóstico e reparo por filial, em DynamoDB (Quarkus) | `/fase4/execution/*` | `build`, `fmt-validate`, `SonarCloud Code Analysis` |
| [fiap-fase4-auth-serverless](https://github.com/CleytonOngaratto/fiap-fase4-auth-serverless) | Borda: API Gateway e a Lambda de autenticação por CPF (Terraform + Node.js) | `/fase4/apigw/*` | `fmt-validate` |

A `main` dos 5 repositórios é protegida pelo ruleset `main-protection`: exige pull request e os checks da tabela, bloqueia
*force push* e exclusão do branch e não tem exceção para ninguém (lido na API do GitHub em 2026-11-09).

O contrato entre os repositórios é o **SSM Parameter Store** sob `/fase4/`: cada um publica o que cria e lê o
que precisa dos outros; nada de ARN, endpoint ou URL fixo no código. Os parâmetros de bootstrap (chaves do
JWT, *pepper* de senha, chaves do New Relic e do Mercado Pago) são publicados uma vez, à mão, e sobrevivem à
destruição do ambiente.

Ordem de deploy: **plataforma → infraestrutura de cada serviço (banco, filas, regras no ALB) → deploy dos
serviços → borda**. Ordem de destroy: **borda de sessão (o API Gateway persistente fica) → ligações do ALB com
os pods → infraestrutura de cada serviço → plataforma**.

## Convenções

- Diagramas em **Mermaid**, renderizados pelo próprio GitHub — nenhuma ferramenta necessária.
- Cada RFC e ADR termina com uma seção **Evidências**: arquivo do repositório que materializa a decisão (ou a
  medição que a sustenta) e a data em que foi verificada.
- Nenhum documento contém segredo, endpoint, hostname de load balancer ou identificador de conta: esses
  valores vivem no SSM Parameter Store e mudam a cada sessão do laboratório.
