# Lab × Produção: o que mudaria numa conta AWS real

> Escrito em 2026-11-09. Cada linha parte de uma restrição do AWS Academy Learner Lab, medida na conta do laboratório ou lida
> no código, e a última coluna aponta onde a decisão está justificada.

O projeto roda num laboratório com orçamento fechado e sem permissão para criar identidades de IAM, e o ambiente é destruído
ao fim de cada sessão. Várias escolhas são as melhores para esse cenário e seriam outras fora dele.

## Identidade e segurança

| Tema | No laboratório | Por que aqui | Numa conta AWS real | Onde está |
|---|---|---|---|---|
| Credencial dos pods | `LabRole` como role do node group, com IMDSv2 em *hop limit* 2 | IRSA e Pod Identity são impossíveis: `iam:CreateRole` e `iam:CreateOpenIDConnectProvider` são negados | IRSA ou EKS Pod Identity com uma role por serviço, e IMDS fora do alcance dos pods (*hop limit* 1) | [ADR-002](adr/ADR-002-credencial-dos-pods.md) |
| Isolamento entre serviços | Cada serviço recebe só o próprio Secret de banco, no próprio namespace. **Limitação:** com a `LabRole` em todos os nós, qualquer pod conseguiria ler no Parameter Store a senha do banco de outro serviço e acessar a tabela do Execution. O isolamento é por convenção e por namespace, não por IAM | Sem IRSA nem Pod Identity, não existe identidade AWS por pod | Uma role por serviço, com acesso só aos próprios parâmetros, à própria tabela e às próprias filas; *security groups for pods*, para que só o pod do serviço alcance o banco dele | [ADR-002](adr/ADR-002-credencial-dos-pods.md) · [ADR-011](adr/ADR-011-bancos-sql-e-nosql.md) |
| Segredos | Parameter Store `SecureString` com a chave gerenciada `aws/ssm`, sem chave própria | Suficiente para o laboratório, e disponível | Secrets Manager com rotação para as senhas de banco e chave KMS própria | [`terraform/ssm.tf`](../../terraform/ssm.tf) e [`terraform/internal.tf`](../../terraform/internal.tf): `SecureString` sem `key_id` (lidos em 2026-11-09) |

## Entrada e rede

| Tema | No laboratório | Por que aqui | Numa conta AWS real | Onde está |
|---|---|---|---|---|
| Entrada | API Gateway → VPC Link → ALB interno, com rotas em allowlist | Integração privada documentada pela AWS, com o ALB no Terraform e nenhum pod de proxy | Igual na essência, mais HTTPS de ponta a ponta, WAF no Gateway ou no ALB e controller com role própria | [ADR-003](adr/ADR-003-borda-privada.md) |
| Domínio e webhook | Gateway persistente, para a URL do webhook não mudar entre sessões | Sem domínio próprio no laboratório | Domínio próprio no API Gateway, com a URL estável por construção | [ADR-004](adr/ADR-004-gateway-persistente.md) |
| Rede | Subnets privadas para tudo, 1 NAT e *gateway endpoints* de S3 e DynamoDB | Boas práticas com custo controlado | NAT por AZ e *interface endpoints* para SQS, SNS, ECR, STS e SSM, sem depender do NAT para falar com a AWS | ["Decisões (resumo)" do infra-k8s](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s#decisões-resumo) |

## Capacidade e ambiente

| Tema | No laboratório | Por que aqui | Numa conta AWS real | Onde está |
|---|---|---|---|---|
| Nós | 2× `t3.large`, em tamanho fixo | A carga coube com folga, medida no EKS. O laboratório só aceita instâncias até `large` e não permite criar a role de um autoscaler de nós | Vários nós em 3 AZs, com Karpenter ou Cluster Autoscaler, instâncias Graviton e Spot onde couber | ["Decisões (resumo)" do infra-k8s](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s#decisões-resumo) |
| Ambiente | Um só, destruído ao fim de cada sessão | Orçamento de US$ 50, e o control plane cobra mesmo parado | Ambientes separados (desenvolvimento, homologação e produção), sempre ligados, com promoção pelo pipeline | [ADR-002 da Fase 3](https://github.com/CleytonOngaratto/fiap-fase3-app/blob/main/docs/arquitetura/adr/ADR-002-ambiente-unico.md) |

## Dados e mensageria

| Tema | No laboratório | Por que aqui | Numa conta AWS real | Onde está |
|---|---|---|---|---|
| Bancos | 2× RDS `db.t3.micro` em uma AZ, com `gp2` | O laboratório não libera `gp3`, *enhanced monitoring* nem Performance Insights | Multi-AZ, backups com recuperação a um ponto no tempo, réplicas de leitura e Performance Insights; DynamoDB com backup contínuo | [ADR-011](adr/ADR-011-bancos-sql-e-nosql.md) · [`terraform/rds.tf`](../../terraform/rds.tf) |
| Outbox do Execution | Partição única (`PENDING`) no índice `gsi-outbox-pending` | Adequada ao volume do projeto | Chave fragmentada (`OUTBOX#<shard>`), para não concentrar a escrita numa partição | [ADR-009](adr/ADR-009-tabela-unica-do-execution.md) |
| Mensageria | SNS FIFO publicando em SQS FIFO, com DLQ | Gerenciada, custo próximo de zero no volume do projeto e provada no laboratório | Igual, mais alarmes de idade da mensagem e de DLQ na própria nuvem e criptografia com chave própria | [ADR-006](adr/ADR-006-mensageria.md) |
| Ordem por grupo FIFO | Lotes de até 10 mensagens, e o consumidor segue depois de uma falha | Risco aceito: entre compensações, a ordem não é regra de negócio | Medir o caminho de ida e, se a ordem morder, parar o lote na primeira falha ou usar `batch-size=1` | [ADR-006](adr/ADR-006-mensageria.md) |

## Observabilidade

| Tema | No laboratório | Por que aqui | Numa conta AWS real | Onde está |
|---|---|---|---|---|
| Métricas da AWS | A profundidade das DLQs vem da própria aplicação (`saga_dlq_messages`) | A integração de nuvem do New Relic exige uma role de IAM entre contas, e `iam:CreateRole` é negado | Integração AWS do New Relic ou CloudWatch Metric Streams, lendo SQS, RDS e ALB direto | [componentes.md](componentes.md#observabilidade) · [ADR-002](adr/ADR-002-credencial-dos-pods.md) |
