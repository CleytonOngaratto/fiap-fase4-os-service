# ADR — API Gateway persistente: a URL pública não muda entre sessões

> Escrito em 2026-10-12, com as medições feitas no AWS Academy em 2026-10-01 e a leitura da documentação do Mercado
> Pago de 2026-09-29.

- **Status:** Aceita (2026-10-01)
- **Decisores:** dono do projeto, com análise e medições do agente
- **Relacionados:** [ADR-003 — borda privada](ADR-003-borda-privada.md) ·
  [catálogo de mensagens §6](../catalogo-de-mensagens.md) (chamadas síncronas) ·
  [borda (fiap-fase4-auth-serverless)](https://github.com/CleytonOngaratto/fiap-fase4-auth-serverless)

## Contexto

O ambiente é **destruído ao fim de cada sessão** do laboratório: o control plane do EKS, o NAT e as instâncias
cobram por hora mesmo parados, e o orçamento do AWS Academy é fechado. Na Fase 3 o API Gateway subia e descia junto
com o resto — e cada recriação gera um identificador novo, ou seja, **uma URL pública nova a cada sessão**.

Na Fase 4 isso deixou de ser só incômodo:

1. **O Mercado Pago notifica pagamentos por webhook**, numa URL HTTPS pública. A URL de notificação se configura no
   painel da aplicação ("Suas integrações") ou por cobrança (`notification_url`). A documentação diz que a URL da
   cobrança **tem prioridade** sobre a do painel, mas **não diz** se a notificação enviada a ela vem com a assinatura
   `x-signature` — e é a assinatura que prova que a notificação veio do Mercado Pago.
2. **Validar a assinatura é obrigatório** no Billing. O caminho seguro é a URL do painel, e o painel não pode ser
   reconfigurado a cada sessão de trabalho.
3. **O HTTP API cobra por requisição**: a página de preço não traz tarifa por hora. Um Gateway parado entre sessões
   custa praticamente zero (inferência pelo modelo de preço, não por fatura medida).

## Decisão

**O HTTP API vive fora do ciclo de sessão.** O repositório da borda tem **dois roots de Terraform**, com states
separados:

| Root | O que cria | Ciclo de vida | Como é aplicado |
|---|---|---|---|
| `persistent/` | o HTTP API, a stage `$default` (*auto deploy*) e o log group de acesso; publica o ID e a URL no SSM (`/fase4/apigw/*`) | **uma vez por conta do laboratório**; fora do teardown de sessão; `prevent_destroy` no HTTP API | só por `workflow_dispatch` |
| `session/` | o VPC Link nas subnets privadas (com security group que só sai para o ALB interno), a integração com o listener do ALB e as **rotas explícitas** | sobe e desce com a sessão; é o primeiro a sair no teardown | `push` na `main` com mudança em `session/`, ou `workflow_dispatch` |

- O `session/` encontra o HTTP API pelo **SSM**, como todo contrato entre repositórios — nunca por
  `terraform_remote_state`.
- As rotas continuam uma **allowlist por recurso** (ADR-003). A criação de usuário (`/auth/signup`) **fica fora**:
  hoje ela aceita os papéis vindos da requisição, e expô-la permitiria criar um ADMIN pela internet.
- A rota `/auth` com a Lambda de CPF volta para o `session/` quando o endpoint interno do OS existir.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| **Gateway por sessão**, como na Fase 3 | a URL muda a cada sessão: reconfigurar o painel do Mercado Pago toda vez, ou depender do `notification_url`, cuja assinatura a documentação não garante |
| **Domínio próprio** no API Gateway (*custom domain*) | exige domínio registrado e certificado ACM; fora do alcance do laboratório |
| **Um root só**, poupando o HTTP API no teardown (`-target` ou remoção manual do state) | destroy parcial por `-target` é frágil e esquecível; dois states deixam o ciclo de vida de cada peça explícito no código |
| **Escolhida:** dois roots, `persistent/` aplicado uma vez e `session/` por sessão | URL estável sem domínio próprio; o teardown de sessão não precisa de exceção, porque o HTTP API está em outro state |

## Consequências

**Positivas**

- A URL pública do Gateway é **estável entre sessões**: o painel do Mercado Pago é configurado uma vez.
- O `session/` é pequeno (VPC Link, uma integração, as rotas) e sobe em minutos; o teardown começa por ele.
- O HTTP API parado não cobra por hora (pelo modelo de preço lido; não medido em fatura).

**Negativas (aceitas e mitigadas)**

- **Um *Reset* do laboratório apaga tudo**, inclusive o HTTP API: a URL muda, o `persistent/` precisa ser aplicado de
  novo e o painel do Mercado Pago reconfigurado.
- **`prevent_destroy` no HTTP API**: um `destroy` do `persistent/` falha de propósito. Apagar de vez exige um PR que
  tira a trava.
- **Ordem obrigatória:** o `persistent/` precisa existir antes do primeiro `session/` — o `session/` lê o ID do HTTP
  API no SSM e falha com mensagem clara se ele não estiver lá.
- **A auditoria de custo precisa reconhecer o que fica**: o teardown lista o HTTP API e o log group dele como
  persistentes por desenho, separados das sobras.
- O log group de acesso persiste entre sessões (retenção de 7 dias).

## Em produção, o que poderia mudar

**Domínio próprio** no API Gateway (certificado ACM), com o webhook apontando para o domínio — a URL fica estável
por construção, e o Gateway pode ser recriado à vontade. Ambientes separados (homologação e produção) com o Gateway
e as rotas versionados no pipeline de cada ambiente.

## Evidências

| Data | Evidência |
|---|---|
| 2026-09-29 | Documentação do Mercado Pago (Checkout Pro › notificações e Webhooks): a URL da cobrança tem prioridade sobre a do painel; a assinatura `x-signature` usa uma chave secreta exclusiva da aplicação; a documentação não diz se as notificações enviadas ao `notification_url` também vêm assinadas |
| 2026-10-01 | Gateway → VPC Link → ALB interno → pod: **HTTP 200** pela internet; rota não declarada → **404 no próprio Gateway** (ver [ADR-003](ADR-003-borda-privada.md)) |
| 2026-10-12 | [`persistent/apigateway.tf`](https://github.com/CleytonOngaratto/fiap-fase4-auth-serverless/blob/main/persistent/apigateway.tf) (HTTP API com `prevent_destroy`), [`session/locals.tf`](https://github.com/CleytonOngaratto/fiap-fase4-auth-serverless/blob/main/session/locals.tf) (allowlist de rotas) e [`destroy-all.ps1`](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s/blob/main/scripts/destroy-all.ps1) (teardown sem o `persistent/`): `terraform fmt`/`validate` sem erro nos dois roots |
