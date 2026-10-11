# ADR — Credencial AWS dos pods: `LabRole` no node group + IMDSv2 com *hop limit* 2

> Escrito em 2026-10-10, com as medições feitas no AWS Academy em 2026-09-29 e confirmado na plataforma real em 2026-10-11.

- **Status:** Aceita (2026-09-29)
- **Decisores:** dono do projeto, com análise e medições do agente
- **Relacionados:** [ADR-003 — borda privada](ADR-003-borda-privada.md) ·
  [catálogo de mensagens](../catalogo-de-mensagens.md) ·
  [plataforma (fiap-fase4-infra-k8s)](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s)

## Contexto

Na Fase 3 a aplicação **nunca chamou a AWS em tempo de execução**: o pipeline lia o SSM e gerava Secrets do
Kubernetes. Na Fase 4 os pods falam com a AWS o tempo todo — publicam no SNS, consomem do SQS, gravam no DynamoDB
e leem parâmetros do SSM. Eles precisam de credencial AWS, e ela precisa:

1. **não expirar com a sessão do laboratório** (as credenciais do AWS Academy duram ~4 h);
2. **não morar em Secret** nem em variável de ambiente;
3. **caber nas restrições do AWS Academy**, medidas na conta do projeto antes de decidir.

O que o laboratório permite e nega (simulação de IAM e testes reais, 2026-09-29):

- o nosso usuário **não pode** criar role (`iam:CreateRole`), anexar ou escrever política (`AttachRolePolicy`,
  `PutRolePolicy`), mudar *trust policy* (`UpdateAssumeRolePolicy`) nem criar provedor OIDC
  (`CreateOpenIDConnectProvider`);
- a `LabRole` confia em ~50 serviços, **entre eles `ec2.amazonaws.com`**, mas **não** em `pods.eks.amazonaws.com`;
  como principal AWS, só aceita a si mesma; pode ser passada (`iam:PassRole`) para o EKS e o EC2;
- a role de nó que o laboratório oferece (`LabEksNodeRole`) tem só as três políticas de *worker*: SNS, SQS,
  DynamoDB e SSM dão `implicitDeny`;
- em node group gerenciado **AL2023 sem launch template**, o *hop limit* do IMDS é 1: *"containers won't have
  access to the node's credentials using IMDS"* (documentação da AWS, *Upgrade from AL2 to AL2023*).

## Decisão

**A `LabRole` é a role do node group**, lida por `data source` (nunca criada), e o node group sobe com um **launch
template próprio** com IMDSv2 obrigatório (`http_tokens = "required"`) e **`http_put_response_hop_limit = 2`**.

O pod está a um salto de rede do nó; com *hop limit* 2, a resposta do IMDSv2 chega até ele, e a cadeia padrão de
credenciais do SDK da AWS encontra a `LabRole` sozinha. **Nenhuma credencial AWS existe em Secret, variável de
ambiente ou arquivo** — nem nos serviços, nem no AWS Load Balancer Controller, nem no agente do New Relic.

## Alternativas consideradas

Todas eliminadas **por medição**, não por preferência:

| Alternativa | Por que não |
|---|---|
| **IRSA** (IAM Roles for Service Accounts) | exige provedor OIDC e uma role por serviço: `CreateOpenIDConnectProvider` e `CreateRole` negados |
| **EKS Pod Identity** | a API existe e é permitida, mas exige uma role que confie em `pods.eks.amazonaws.com`: a `LabRole` não confia, e não podemos criar nem alterar role |
| **Role de nó padrão** (`LabEksNodeRole`) via IMDS | só tem as políticas de *worker*; SNS, SQS, DynamoDB e SSM dão `implicitDeny`, e não podemos anexar política a ela |
| **Assumir a `LabRole`** a partir da role do nó | a *trust policy* da `LabRole` só aceita a própria `LabRole` como principal AWS |
| **Credencial da sessão** num Secret do Kubernetes | funciona, mas expira com a sessão (~4 h): todo início de sessão exigiria regravar o Secret e reiniciar os pods — e é exatamente o segredo que a decisão quer evitar. Fica como plano B |

## Consequências

**Positivas**

- Nenhum segredo de credencial no cluster, no Git ou no pipeline; nada expira no meio da sessão.
- Zero código específico: os serviços usam a cadeia padrão de credenciais do SDK, a mesma que usariam com IRSA.
- A mesma regra vale para tudo que roda no cluster (serviços, controller, agentes).

**Negativas (aceitas, documentadas)**

- **Todo pod do nó herda a `LabRole`**, que tem permissões amplas, parecidas com as do usuário do console. Não há
  *least privilege* por serviço.
- Por consequência, o **isolamento entre serviços é por namespace e por convenção, não por IAM**: cada serviço
  recebe só o próprio Secret de banco, no próprio namespace, e nunca lê segredo de outro — mas qualquer pod
  *conseguiria* ler do SSM a senha do banco de outro serviço ou a tabela DynamoDB do Execution. A regra "nenhum
  serviço acessa o banco de outro" é garantida pela arquitetura (cada serviço só conhece o próprio banco), não por
  uma barreira de IAM.
- O *hop limit* 2 é **obrigatório**: um node group recriado sem o launch template volta ao *hop limit* 1 e todos os
  pods ficam sem credencial (`NoCredentials`), com o nó perfeitamente saudável.
- Mitigação possível no laboratório, **não testada**: `NetworkPolicy` bloqueando `169.254.169.254` para os pods que
  não precisam da AWS (exige ligar o suporte a NetworkPolicy no VPC CNI).

## Em produção, o que poderia mudar

**IRSA ou EKS Pod Identity com uma role por serviço**, cada uma com acesso só aos próprios parâmetros do SSM, à
própria tabela e às próprias filas; a role do nó volta a ter só as políticas de *worker*, e o IMDS fica fora do
alcance dos pods (*hop limit* 1). Para a rede, *security groups for pods* fariam só o pod de um serviço alcançar o
banco dele.

## Evidências

| Data | Evidência |
|---|---|
| 2026-09-29 | Simulação de IAM na conta do laboratório: `LabEksNodeRole` com `implicitDeny` para `sns:Publish`, `sqs:ReceiveMessage`, `dynamodb:PutItem` e `ssm:GetParameter`; `LabRole` com todas permitidas; `iam:CreateRole` e `iam:CreateOpenIDConnectProvider` negados ao nosso usuário |
| 2026-09-29 | Node group AL2023 com a `LabRole` + launch template com *hop limit* 2: a API aceitou, o nó ficou `Ready` e o EKS criou sozinho a *access entry* do nó; um pod **sem credencial** se apresentou como `assumed-role/LabRole/<instância>` e publicou no SNS, gravou no DynamoDB, leu do SQS e consultou o SSM |
| 2026-09-29 | Controle: mesmo pod, mesma instância, *hop limit* 1 → `NoCredentials: Unable to locate credentials` |
| 2026-10-11 | [`eks.tf`](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s/blob/main/eks.tf) aplicado pelo pipeline da plataforma: as duas instâncias do node group conferidas na API do EC2 — IMDSv2 `required`, *hop limit* 2, crédito `unlimited`, só o security group do cluster, sem IP público |
| 2026-10-11 | Pod com a imagem oficial do AWS CLI e **nenhuma** variável de credencial: `assumed-role/LabRole/<instância>`; publicou em `billing-events.fifo`, recebeu a mensagem crua numa fila FIFO assinada ao tópico e gravou e leu no DynamoDB |
