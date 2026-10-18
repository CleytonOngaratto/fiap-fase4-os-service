# Documentação arquitetural — Fase 4 (microsserviços com Saga)

Evolução da Car Workshop API (Fase 3, um monolito no EKS) para **três microsserviços independentes** — OS,
Billing e Execution —, cada um com repositório, infraestrutura e banco próprios, coordenados por uma **Saga
orquestrada** sobre SNS/SQS FIFO, com pagamento pelo Mercado Pago, na AWS.

A documentação da Fase 3 continua válida como **estado anterior** e não foi copiada para cá:
[fiap-fase3-app/docs/arquitetura](https://github.com/CleytonOngaratto/fiap-fase3-app/tree/main/docs/arquitetura)
(nuvem, banco, autenticação por CPF e os ADRs daquela fase). Os documentos abaixo descrevem o que muda.

Os documentos nascem junto com o código, um bloco por vez; os que ainda não existem aparecem como
*a escrever*, com o bloco em que entram.

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
| [adr/ADR-006-mensageria.md](adr/ADR-006-mensageria.md) | Por que SNS FIFO publicando em SQS FIFO com outbox transacional, o que isso custa (bloqueio de cabeça de fila, idempotência obrigatória) e o que ficou como dívida registrada |
| `sequencia.md` | *a escrever (B7)* — sequência da Saga: caminho feliz e compensação |
| `banco-de-dados.md` | *a escrever (B4–B6)* — Postgres do OS e do Billing, DynamoDB do Execution, outbox e inbox |
| `lab-vs-producao.md` | *a escrever (B9)* — o que mudaria fora do AWS Academy, decisão por decisão |
| ADRs restantes | *a escrever, cada um no bloco que implementa a decisão* — Lambda × banco do OS (B4), bancos SQL + NoSQL (B4–B6) |

## Onde está cada tema pedido no Tech Challenge

| Tema do enunciado | Documento |
|---|---|
| Três ou mais microsserviços independentes, cada um com repositório, infraestrutura e banco próprios | [componentes.md](componentes.md) · [ADR-005](adr/ADR-005-divisao-em-servicos.md) · [Repositórios](#repositórios) |
| OS Service: abertura, atualização de status, consulta de status e histórico | [maquina-de-estados.md §1](maquina-de-estados.md) |
| Billing: orçamento para aprovação, pagamentos e atualização da OS após o pagamento | [maquina-de-estados.md §3](maquina-de-estados.md) · [catalogo-de-mensagens.md §3–4](catalogo-de-mensagens.md) |
| Execution: fila de execução, status durante diagnóstico e reparo, finalização comunicada ao OS | [maquina-de-estados.md §3](maquina-de-estados.md) · [catalogo-de-mensagens.md §4](catalogo-de-mensagens.md) |
| Integração com o Mercado Pago | [catalogo-de-mensagens.md](catalogo-de-mensagens.md) (cobrança, confirmação e estorno) · [matriz-de-compensacao.md](matriz-de-compensacao.md) (F4–F8) |
| Banco próprio por serviço, ao menos um SQL e um NoSQL | `banco-de-dados.md` (B4–B6) |
| Nenhum serviço acessa o banco de outro | [catalogo-de-mensagens.md §6](catalogo-de-mensagens.md) (o que é síncrono e por quê) · [ADR-002](adr/ADR-002-credencial-dos-pods.md) (isolamento de credenciais no laboratório) · [componentes.md](componentes.md) |
| Comunicação REST síncrona quando necessário e mensageria assíncrona | [catalogo-de-mensagens.md §1 e §6](catalogo-de-mensagens.md) · [ADR-006](adr/ADR-006-mensageria.md) |
| Saga com rollback e compensação em qualquer etapa | [matriz-de-compensacao.md](matriz-de-compensacao.md) · [maquina-de-estados.md §2](maquina-de-estados.md) |
| Saga orquestrada ou coreografada, com a escolha justificada | [ADR-001](adr/ADR-001-saga-orquestrada.md) |
| Diagrama geral e sequência da Saga | [componentes.md](componentes.md) · `sequencia.md` (B7) |
| Rastreamento dos fluxos distribuídos | [catalogo-de-mensagens.md §2](catalogo-de-mensagens.md) (`correlationId` e `sagaId` em toda mensagem) |

## Ordem de leitura sugerida

1. [ADR-001](adr/ADR-001-saga-orquestrada.md) — a decisão central e o porquê.
2. [maquina-de-estados.md](maquina-de-estados.md) — os estados que a saga move.
3. [catalogo-de-mensagens.md](catalogo-de-mensagens.md) — as mensagens que movem esses estados.
4. [matriz-de-compensacao.md](matriz-de-compensacao.md) — o que acontece quando cada etapa falha.

## Repositórios

| Repositório | Conteúdo | Publica no SSM (planejado) |
|---|---|---|
| [fiap-fase4-infra-k8s](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s) | Plataforma: VPC, EKS, ECR, ALB interno e tópicos SNS (Terraform) | `/fase4/vpc/*`, `/fase4/eks/*`, `/fase4/ecr/*`, `/fase4/sns/*`, `/fase4/alb/*` |
| [fiap-fase4-os-service](https://github.com/CleytonOngaratto/fiap-fase4-os-service) | OS, cadastros, filiais, autenticação do ADMIN e o orquestrador da Saga (Quarkus) — este repositório | `/fase4/os/*` |
| [fiap-fase4-billing-service](https://github.com/CleytonOngaratto/fiap-fase4-billing-service) | Orçamento, aprovação pelo cliente e pagamento com Mercado Pago (Quarkus) | `/fase4/billing/*` |
| [fiap-fase4-execution-service](https://github.com/CleytonOngaratto/fiap-fase4-execution-service) | Fila de diagnóstico e reparo por filial, em DynamoDB (Quarkus) | `/fase4/execution/*` |
| [fiap-fase4-auth-serverless](https://github.com/CleytonOngaratto/fiap-fase4-auth-serverless) | Borda: API Gateway e a Lambda de autenticação por CPF (Terraform + Node.js) | `/fase4/apigw/*` |

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
