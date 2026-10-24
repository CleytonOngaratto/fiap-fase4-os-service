# fiap-fase4-os-service

Ordem de Serviço (OS Service) da Fase 4 do Tech Challenge (FIAP SOAT): abertura, status e histórico da OS,
cadastros, filiais e o orquestrador da Saga. Quarkus.

> Em construção. Evolução de [fiap-fase3-app](https://github.com/CleytonOngaratto/fiap-fase3-app). Arquitetura em
> [`docs/arquitetura/`](docs/arquitetura/README.md).

## O que este serviço faz

| Responsabilidade | Onde |
|---|---|
| Abrir uma ordem de serviço (cliente, veículo, **relato** e **filial**), consultar status e **histórico** | [`workorder/`](src/main/java/br/com/fiap/postech/carworkshop/workorder) |
| Cadastros e catálogo: clientes, veículos, serviços, peças e estoque | `customer/`, `vehicle/`, `autoservice/`, `inventory/` |
| Filiais onde uma ordem pode ser aberta | [`branch/`](src/main/java/br/com/fiap/postech/carworkshop/branch) |
| Autenticação do atendimento (ADMIN) e o endpoint interno que confirma um CPF para a Lambda de clientes | [`auth/`](src/main/java/br/com/fiap/postech/carworkshop/auth), `customer/adapter/controller` |
| **Orquestrador da Saga**: máquina de estados, prazos, compensações, outbox e inbox | [`saga/`](src/main/java/br/com/fiap/postech/carworkshop/saga) |

A escolha de **Saga orquestrada**, com o orquestrador em código aqui dentro, está justificada no
[ADR-001](docs/arquitetura/adr/ADR-001-saga-orquestrada.md); as três máquinas de estado envolvidas, na
[máquina de estados](docs/arquitetura/maquina-de-estados.md); as tabelas, em
[banco-de-dados.md](docs/arquitetura/banco-de-dados.md).

## Deploy

Três workflows, cada um com um papel:

| Workflow | Gatilho | O que faz |
|---|---|---|
| [`maven-ci.yml`](.github/workflows/maven-ci.yml) | PR e `push` na `main` | build, testes, JaCoCo, análise no SonarCloud (`build` e `SonarCloud Code Analysis` são checks obrigatórios) e Trivy |
| [`terraform.yml`](.github/workflows/terraform.yml) | PR → `fmt-validate` (check obrigatório) + `plan` · `push` na `main` com mudança em `terraform/` → `apply` · `workflow_dispatch` → `plan`/`apply`/`destroy` | a infraestrutura do serviço, em [`terraform/`](terraform/): RDS PostgreSQL, target group `ip` e a regra `/carworkshop/v1/*` no ALB interno da plataforma |
| [`cd.yml`](.github/workflows/cd.yml) | `push` na `main` (fora `*.md`, `docs/` e `terraform/`) e `workflow_dispatch` | testes → imagem no ECR (tag = SHA do commit) → manifestos no EKS → smoke: alvos `healthy` no target group e, com a borda de pé, `GET /carworkshop/v1/tracking/1` sem token → **401** pelo API Gateway |

**O merge aplica.** Um merge que mude `terraform/` cria ou altera recursos na AWS — só com a sessão do laboratório
ligada e com o teardown na mesma sessão. Num merge com código e `terraform/` os dois workflows rodam juntos: o
`cd.yml` espera o próprio contrato no SSM (até 25 min) e falha dizendo quem publica.

⚠️ **Este serviço depende das filas dos outros.** O `cd.yml` espera as filas de comandos publicadas pelo
`fiap-fase4-execution-service` e pelo `fiap-fase4-billing-service`, porque é para lá que o relay do outbox
envia. Na subida de uma sessão, os dois participantes vão **antes** do OS.

## Mensagens

O orquestrador envia **comandos** para a fila de cada participante e reage aos **eventos** que eles publicam.
Todo comando sai pelo outbox; a fila de destino é resolvida pelo tipo da mensagem.

| Publica | Para onde |
|---|---|
| `StartDiagnosis`, `CancelDiagnosis`, `StartRepair` | `execution-commands.fifo` (fila do Execution) |
| `CreateBudget`, `CancelBudget`, `RequestPayment`, `CancelPayment`, `RefundPayment` | `billing-commands.fifo` (fila do Billing) |

| Consome | Efeito no processo |
|---|---|
| `DiagnosisStarted` | a ordem passa para `UNDER_DIAGNOSIS` |
| `DiagnosisCompleted` | precifica os itens pelo catálogo local e pede o orçamento |
| `BudgetCreated` | a ordem passa para `PENDING_APPROVAL` e abre o prazo de aprovação |
| `BudgetApproved` | **reserva** as peças e pede a cobrança |
| `PaymentRequested` | abre o prazo de pagamento |
| `PaymentConfirmed` | **baixa** as peças reservadas e manda reparar; se a saga já compensou, estorna na hora |
| `RepairStarted`, `RepairCompleted` | acompanha o reparo; concluído é o **pivô**: a ordem vira `COMPLETED` |
| `DiagnosisFailed`, `BudgetCreationFailed`, `BudgetRejected`, `PaymentRequestFailed`, `RepairFailed` | compensa, com o motivo registrado |
| `DiagnosisCanceled`, `BudgetCanceled`, `PaymentCanceled`, `PaymentRefunded` | confirma uma compensação pendente |
| `PaymentAttemptRejected` | informativo: registra a tentativa e segue esperando o prazo |

Todos chegam por `os-saga-replies.fifo`, a fila deste serviço assinada aos tópicos dos participantes.

O envelope e o significado de cada campo estão no
[catálogo de mensagens](docs/arquitetura/catalogo-de-mensagens.md); o porquê da topologia, no
[ADR-006](docs/arquitetura/adr/ADR-006-mensageria.md).

**Nada é publicado fora de transação.** A mensagem é gravada numa tabela de saída (*outbox*) **na mesma
transação** que cria a ordem de serviço — o adaptador exige transação ativa e falha se não houver, em vez de
abrir uma própria e virar escrita dupla. Um *relay* publica as pendentes e marca cada uma como enviada,
reivindicando as linhas com `FOR UPDATE SKIP LOCKED` para que várias réplicas drenem a fila sem publicar a mesma
mensagem duas vezes.

**O consumidor é idempotente duas vezes.** Uma tabela de entrada (*inbox*) registra o identificador da mensagem
**na mesma transação** do efeito: a mesma resposta reentregue não produz efeito nenhum. Além disso, cada
transição do domínio é um não-operação quando o estado já passou dela — resposta fora de ordem não derruba a
ordem de serviço nem vai para a fila de descarte. Resposta para uma ordem inexistente é inconsistência real, e
essa sim fica para o descarte, onde dá para investigar.

**Nenhum comando sem fila bloqueia os outros.** A linha do outbox guarda o destino, e a consulta do relay só
reivindica destinos com fila configurada — sem isso, um comando para um participante ainda não provisionado
ocuparia as vagas do lote e as mensagens atrás dele nunca sairiam.

**O que falha é compensado, não repetido para sempre.** Falta de estoque na reserva é regra de negócio: vira
compensação com motivo `OUT_OF_STOCK`, em vez de reentregar a mensagem até o descarte. Já indisponibilidade de
um participante **não** é resposta de falha: a mensagem espera na fila e a saga retoma de onde parou.

**A ordem só fica cancelada quando a última compensação é confirmada.** Enquanto falta uma, o estado da saga
mostra que ela está compensando e o cliente não vê "cancelado" com dinheiro ainda retido. A ordem das
compensações e o estado final de cada falha estão na
[matriz de compensação](docs/arquitetura/matriz-de-compensacao.md).

## Ambiente local

`docker compose up` sobe o serviço com um **Postgres** e um **ElasticMQ** (API compatível com a de filas da AWS),
com as quatro filas FIFO — comandos, respostas e as duas de descarte — já declaradas em
[`docker/elasticmq/elasticmq.conf`](docker/elasticmq/elasticmq.conf). O contêiner da aplicação roda com o perfil
de produção, então recebe o endereço do emulador, uma credencial fictícia e as URLs das filas por variável de
ambiente. Abrir uma ordem de serviço com tudo de pé é suficiente para ver o outbox sendo drenado para a fila.

Não há emulador de tópicos: a publicação no tópico é verificada por teste de contrato e por um dublê da porta de
saída. Os testes de mensageria sobem os contêineres sozinhos (exigem o Docker em execução).

## Contrato no SSM

| Lê | De quem |
|---|---|
| `/fase4/vpc/id`, `/fase4/vpc/private-subnets`, `/fase4/eks/node-sg-id`, `/fase4/alb/listener-arn`, `/fase4/sns/{execution-events,billing-events}/topic-arn` | plataforma ([fiap-fase4-infra-k8s](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s)), no `terraform/` |
| `/fase4/ecr/os/repo-url`, `/fase4/eks/cluster-name`, `/fase4/alb/sg-id` | plataforma, no `cd.yml` |
| `/fase4/execution/sqs/commands-queue-url` | **[fiap-fase4-execution-service](https://github.com/CleytonOngaratto/fiap-fase4-execution-service)**, no `cd.yml` — fila de comandos do Execution |
| `/fase4/billing/sqs/commands-queue-url` | **[fiap-fase4-billing-service](https://github.com/CleytonOngaratto/fiap-fase4-billing-service)**, no `cd.yml` — fila de comandos do Billing |
| `/fase4/jwt/{private-key,public-key}`, `/fase4/app/secret-key`, `/fase4/newrelic/license-key` | bootstrap manual (sobrevive ao teardown) |

| Publica | Conteúdo |
|---|---|
| `/fase4/os/alb/target-group-arn` | target group que o `TargetGroupBinding` liga aos pods |
| `/fase4/os/rds/{endpoint,port,db-name,username}` | banco do serviço — endpoint só como host |
| `/fase4/os/rds/password` | SecureString; lida só pelo `cd.yml` deste repositório |
| `/fase4/os/sqs/saga-replies-queue-url` | fila de respostas do orquestrador, assinada aos tópicos de eventos dos participantes |
| `/fase4/os/internal/service-token` | SecureString; credencial do endpoint interno, lida por este `cd.yml` e pela Lambda de autenticação ([ADR-007](docs/arquitetura/adr/ADR-007-lambda-sem-banco-do-os.md)) |

## Kubernetes

Tudo no namespace **`os`** ([`k8s/application/`](k8s/application/)): ServiceAccount própria sem token montado,
ConfigMap, Secrets (banco, chave da aplicação e New Relic, par JWT) gerados pelo `cd.yml` a partir do SSM e que só
existem neste namespace, Deployment com HPA, Service `ClusterIP` e o `TargetGroupBinding` que registra os pods no
target group do ALB interno. Nenhum load balancer é criado pelo cluster.

O namespace tem a label `elbv2.k8s.aws/pod-readiness-gate-inject: enabled`: o pod só fica pronto quando o alvo dele
está `healthy` no target group, e o `preStop` segura o encerramento enquanto o ALB drena o alvo — o rollout não
derruba requisição.

A entrada é sempre pelo API Gateway (rotas explícitas por recurso); `/q/*` (health, métricas, OpenAPI) não passa
pelo Gateway e só é usado dentro da VPC.

## Rotas que não são públicas

`GET /carworkshop/v1/internal/customers/{cpf}` confirma, para a Lambda de autenticação, que um CPF pertence a um
cliente — para que ela **não** leia o banco deste serviço. A rota fica fora da allowlist do Gateway **e** exige
uma credencial de serviço na própria aplicação, com comparação em tempo constante; sem o segredo configurado ela
nega todo mundo. Um JWT de ADMIN válido não substitui a credencial. O porquê das duas camadas está no
[ADR-007](docs/arquitetura/adr/ADR-007-lambda-sem-banco-do-os.md).

`POST /carworkshop/v1/auth/signup` cria administradores e **não aceita papéis vindos da requisição**: o papel é
decidido no servidor. Exige um administrador autenticado, com uma exceção de primeiro acesso — enquanto não
existe nenhum usuário, a chamada anônima cria o primeiro, registrando isso no log. O Gateway não publica esta
rota.
