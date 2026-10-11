# ADR — Borda privada: API Gateway → VPC Link → ALB interno → pods

> Escrito em 2026-10-10, com as medições feitas no AWS Academy em 2026-10-01 e confirmado na plataforma real em 2026-10-11.

- **Status:** Aceita (2026-10-01)
- **Decisores:** dono do projeto, com análise e medições do agente
- **Relacionados:** [ADR-002 — credencial dos pods](ADR-002-credencial-dos-pods.md) ·
  [catálogo de mensagens §6](../catalogo-de-mensagens.md) (chamadas síncronas) ·
  [plataforma (fiap-fase4-infra-k8s)](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s)

## Contexto

Três serviços precisam de **uma** entrada pública em HTTPS: o cliente (token JWT por CPF), o atendimento e o
mecânico (token de ADMIN) e o **webhook do Mercado Pago**, que só notifica URL HTTPS pública. Na Fase 3 a aplicação
era exposta por um `Service` do tipo `LoadBalancer` público, com o API Gateway na frente
([ADR-003 da Fase 3](https://github.com/CleytonOngaratto/fiap-fase3-app/blob/main/docs/arquitetura/adr/ADR-003-exposicao-da-app-lb-publico.md)).

O que mudou e o que pesou:

1. **Três serviços, não um.** Um load balancer por serviço triplica custo e superfície pública.
2. **O ingress-nginx foi aposentado em março de 2026** — sem releases, correções nem patches de segurança depois
   disso; a recomendação oficial é migrar para a Gateway API (blog do Kubernetes, 2025-11-11 e 2026-01-29).
3. **Restrições do AWS Academy:** nada de role própria para um controlador (sem IRSA — ver ADR-002), e as ações de
   `shield:*` são negadas explicitamente à `LabRole`.
4. **O cluster não deveria ter entrada pública nenhuma**: os nós e os pods vivem em subnets privadas.

## Decisão

**API Gateway HTTP API → VPC Link → ALB interno → pods**, sem nenhum load balancer público.

| Peça | Onde | Dono |
|---|---|---|
| API Gateway HTTP API (TLS, rotas, JWT do cliente na Lambda) | regional, público | `fiap-fase4-auth-serverless` |
| VPC Link e integrações | subnets privadas | `fiap-fase4-auth-serverless` |
| ALB **interno**, listener HTTP com resposta padrão **404** e security group do ALB | subnets privadas | `fiap-fase4-infra-k8s` (Terraform) |
| Target group `ip` + regra de path no listener + `TargetGroupBinding` | subnets privadas / cluster | repositório de cada serviço |

- O **AWS Load Balancer Controller 3.5.0** roda **só para `TargetGroupBinding`**: liga os pods de cada serviço ao
  target group dele. Não cria load balancer, não atende `Ingress` (sem `IngressClass`), não intercepta `Service` do
  tipo `LoadBalancer` (webhook de Service desligado) e roda com Shield, WAF e WAFv2 desligados. A credencial dele é
  a `LabRole`, pelo IMDS (ADR-002).
- As **rotas do Gateway são uma allowlist explícita por recurso** (ex.: `ANY /carworkshop/v1/work-orders/{proxy+}`),
  nunca `/{proxy+}` nem `/carworkshop/{proxy+}`.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| **Um `Service LoadBalancer` por serviço** (3 load balancers) | custo e superfície triplicados; ou ficam públicos, ou exigem três integrações privadas |
| **Controlador de entrada no cluster** com 1 load balancer (Traefik, Envoy Gateway com Gateway API) | um proxy a mais dentro do cluster, consumindo memória dos nós e exigindo operação, e ainda assim com um load balancer na frente |
| **ingress-nginx** | aposentado em março de 2026, sem patch de segurança |
| **Escolhida:** ALB interno no Terraform + `TargetGroupBinding` | padrão de integração privada documentado pela AWS (VPC Link de HTTP API integra com ALB, com roteamento por path); um load balancer só, privado; nenhum proxy no cluster |

## Consequências

**Positivas**

- **Nenhuma entrada pública no cluster**: o DNS do ALB resolve para IPs privados e não aceita conexão de fora da VPC.
- Um load balancer para os três serviços, criado pelo Terraform da plataforma: o endereço vai para o SSM no `apply`,
  sem o parâmetro publicado à mão pelo pipeline, como na Fase 3.
- O controller é leve (~25 Mi em repouso) e mexe só no necessário: ao criar um binding, abre a porta do pod no
  security group do cluster **apenas para o security group do ALB** — e a fecha quando o binding é apagado.

**Negativas (aceitas e mitigadas)**

- **Dentro da VPC o tráfego é HTTP**: o TLS termina no API Gateway. Aceitável num ambiente de laboratório isolado;
  em produção, ver abaixo.
- **A allowlist do Gateway é necessária, mas não suficiente.** Ela barra `../` e as variantes codificadas, mas
  repassa `..;/` literalmente ao backend. Se o servidor resolver esse segmento, a allowlist é contornada — por isso
  todo endpoint interno exige **autenticação de serviço na aplicação**, e o teste com o servidor real é obrigatório
  quando o endpoint interno nascer.
- **Toda rota nova precisa ser declarada no Gateway**: uma rota esquecida é um 404, não uma exposição.
- **Certificado do webhook gerado com a hora local**: o Helm cria o certificado do webhook do controller com o
  relógio da máquina que instala; com o relógio adiantado, o API server o recusa por alguns minutos. O script de
  instalação mede a diferença contra a hora da AWS e aborta antes.
- O controller herda as permissões amplas da `LabRole` (ADR-002).

## Em produção, o que poderia mudar

**HTTPS de ponta a ponta** (certificado ACM no ALB), **WAF** no Gateway ou no ALB, **domínio próprio** no API
Gateway e o controller com **role própria** (IRSA ou Pod Identity) com só as permissões de `TargetGroupBinding`.

## Evidências

| Data | Evidência |
|---|---|
| 2026-10-01 | Política IAM oficial do AWS Load Balancer Controller (80 ações) simulada contra a `LabRole`: 74 permitidas; negadas `iam:CreateServiceLinkedRole` (a *service-linked role* do ELB já existe), `shield:*` ×4 (`explicitDeny`) e `ModifyCapacityReservation` (recurso não usado) |
| 2026-10-01 | Controller 3.5.0 instalado com Shield/WAF/WAFv2 e o webhook de Service desligados: `Running`, nenhum erro de permissão no log, ~25 Mi de memória |
| 2026-10-01 | ALB interno + target group `ip` + `TargetGroupBinding`: os pods ficaram `healthy`; o controller criou a regra mínima no security group do cluster (porta do pod só a partir do SG do ALB) e a removeu ao apagar o binding |
| 2026-10-01 | Gateway → VPC Link → ALB interno → pod: **HTTP 200** pela internet; rota não declarada → **404 no próprio Gateway**; DNS do ALB acessado direto → **sem conexão** |
| 2026-10-01 | Rotas do Gateway: uma rota genérica expôs tudo abaixo dela (inclusive `/q/metrics`); uma rota declarada **sem integração não bloqueou nada**; a allowlist barrou `../`, `%2e%2e/`, `..%2f` e `%2e%2e%2f`, mas repassou `..;/` literalmente; casamento de rota sensível a maiúsculas |
| 2026-10-11 | [`alb.tf`](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s/blob/main/alb.tf) aplicado pelo pipeline da plataforma: o nome público do ALB resolve só para IPs privados; de dentro de um pod, `HTTP 404` (resposta padrão do listener); pela internet, **sem conexão** |
| 2026-10-11 | [values](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s/blob/main/k8s/aws-load-balancer-controller/values.yaml) e [script de instalação](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s/blob/main/scripts/install-lb-controller.ps1) do controller 3.5.0: 2 réplicas `Running`, nenhum erro no log, sem `IngressClass` e sem webhook de Service; o webhook de `TargetGroupBinding` respondeu por TLS a um teste `--dry-run=server` — instalado com o relógio local alinhado à AWS (diferença medida: 0 s) |
