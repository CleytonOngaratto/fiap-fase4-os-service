# fiap-fase4-os-service

Ordem de Serviço (OS Service) da Fase 4 do Tech Challenge (FIAP SOAT): abertura, status e histórico da OS,
cadastros, filiais e o orquestrador da Saga. Quarkus.

> Em construção. Evolução de [fiap-fase3-app](https://github.com/CleytonOngaratto/fiap-fase3-app). Arquitetura em
> [`docs/arquitetura/`](docs/arquitetura/README.md).

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

⚠️ **Este serviço depende da fila de outro.** O `cd.yml` espera também a fila de comandos publicada pelo
`fiap-fase4-execution-service`, porque é para lá que o relay do outbox envia. Na subida de uma sessão, o
Execution vai **antes** do OS.

## Mensagens

| Publica | Para onde | Quando |
|---|---|---|
| `StartDiagnosis` | `execution-commands.fifo` (fila do Execution) | ao abrir uma ordem de serviço |

| Consome | De onde | Efeito |
|---|---|---|
| `DiagnosisStarted` | `os-saga-replies.fifo` (fila deste serviço, assinada aos tópicos dos participantes) | a ordem de serviço passa de `RECEIVED` para `UNDER_DIAGNOSIS` |

O envelope e o significado de cada campo estão no
[catálogo de mensagens](docs/arquitetura/catalogo-de-mensagens.md); o porquê da topologia, no
[ADR-006](docs/arquitetura/adr/ADR-006-mensageria.md).

**Nada é publicado fora de transação.** A mensagem é gravada numa tabela de saída (*outbox*) **na mesma
transação** que cria a ordem de serviço — o adaptador exige transação ativa e falha se não houver, em vez de
abrir uma própria e virar escrita dupla. Um *relay* publica as pendentes e marca cada uma como enviada,
reivindicando as linhas com `FOR UPDATE SKIP LOCKED` para que várias réplicas drenem a fila sem publicar a mesma
mensagem duas vezes.

**O consumidor é idempotente por estado.** Receber a mesma resposta duas vezes avança o status uma vez só e a
mensagem é apagada; receber resposta para uma ordem de serviço inexistente é inconsistência real, então a
mensagem fica para a fila de descarte, onde dá para investigar.

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
| `/fase4/execution/sqs/commands-queue-url` | **[fiap-fase4-execution-service](https://github.com/CleytonOngaratto/fiap-fase4-execution-service)**, no `cd.yml` — é a fila para onde o relay envia os comandos |
| `/fase4/jwt/{private-key,public-key}`, `/fase4/app/secret-key`, `/fase4/newrelic/license-key` | bootstrap manual (sobrevive ao teardown) |

| Publica | Conteúdo |
|---|---|
| `/fase4/os/alb/target-group-arn` | target group que o `TargetGroupBinding` liga aos pods |
| `/fase4/os/rds/{endpoint,port,db-name,username}` | banco do serviço — endpoint só como host |
| `/fase4/os/rds/password` | SecureString; lida só pelo `cd.yml` deste repositório |
| `/fase4/os/sqs/saga-replies-queue-url` | fila de respostas do orquestrador, assinada aos tópicos de eventos dos participantes |

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
