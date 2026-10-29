# ADR — Pagamento pelo Checkout Pro do Mercado Pago

- **Status:** Aceita (2026-10-25, implementada no bloco do Billing Service)
- **Decisores:** dono do projeto, com análise e medições do agente
- **Relacionados:** [catálogo de mensagens §3–4](../catalogo-de-mensagens.md) ·
  [matriz de compensação F4–F8](../matriz-de-compensacao.md) ·
  [máquina de estados §3](../maquina-de-estados.md) ·
  [ADR-004 — gateway persistente](ADR-004-gateway-persistente.md)

## Contexto

O enunciado pede pagamento real com um provedor externo. O Billing Service precisa criar a cobrança quando o
cliente aprova o orçamento, saber quando ela foi paga e **saber desfazê-la** — porque pagamento é a etapa mais
cara de compensar numa Saga: depois de confirmado, o único caminho de volta é o estorno.

Três restrições moldam a decisão:

1. **O ambiente é um laboratório de ~4 h.** A URL do cluster muda a cada sessão, mas o webhook do provedor é
   configurado **uma vez**, no painel dele. Sem uma URL estável, cada sessão exigiria reconfigurar o painel à
   mão.
2. **Não há cartão real.** Todo o fluxo usa as contas de teste do provedor, e os cartões de teste decidem o
   resultado pelo nome do portador (`APRO` aprova, `FUND` recusa por saldo).
3. **A confirmação é assíncrona e chega de fora.** O pagamento não termina numa resposta HTTP nossa: ele
   termina numa notificação que o provedor envia quando quiser, quantas vezes quiser.

## Decisão

**Checkout Pro**, a integração por redirecionamento: o Billing cria uma *preferência* e devolve ao cliente o
link `init_point`; o pagamento acontece na página do provedor.

Quatro chamadas HTTP, e só quatro:

| # | Chamada | Para quê |
|---|---|---|
| 1 | `POST /checkout/preferences` | cria a cobrança; devolve `id` e `init_point` |
| 2 | `GET /v1/payments/{id}` | **única** fonte da verdade sobre o status |
| 3 | `PUT /v1/payments/{id}` com `{"status":"cancelled"}` | cancela cobrança ainda não paga |
| 4 | `POST /v1/payments/{id}/refunds` | estorna cobrança já paga |

Quatro regras sustentam o desenho:

**O corpo da notificação nunca é fonte de verdade.** A notificação diz apenas *qual* pagamento mudou; o
status vem sempre da chamada 2. Quem confia no corpo recebido confia em algo que não autenticou.

**A preferência não carrega `notification_url`.** A URL configurada na cobrança teria prioridade sobre a do
painel, e voltaríamos a depender de uma URL que muda a cada sessão. O webhook chega pela URL estável do API
Gateway persistente ([ADR-004](ADR-004-gateway-persistente.md)).

**O cancelamento é best-effort; a compensação não é.** O provedor só aceita cancelar pagamento em
`pending`, `in_process` ou `authorized` — fora disso responde com o erro **2018**. Isso **não** é falha: é
mudança de caminho. O `PaymentCanceled` é publicado para o orquestrador **mesmo quando o provedor recusa o
cancelamento**; só falha de transporte (5xx esgotado, timeout) vai para a DLQ. Sem essa regra a saga ficaria
em `COMPENSATING` para sempre esperando uma confirmação que nunca viria.

**Todo estorno usa a mesma chave de idempotência.** Ela nasce com o pagamento (`refund_idempotency_key`,
UUID v4 persistido) e vai no header `X-Idempotency-Key` em toda tentativa — a do retry e a do estorno
automático. É o que garante que retentar a compensação não estorne duas vezes.

### Pagamento aprovado que chega depois do cancelamento

É a corrida mais incômoda do fluxo: o prazo estoura, a saga compensa, e o cliente paga mesmo assim — o link
ainda estava aberto no navegador dele. O tratamento vive em **duas camadas complementares**, e as duas
existem de propósito:

| Camada | Quando age | O que faz |
|---|---|---|
| **Billing** (webhook) | o orçamento **já está** `CANCELED` quando a notificação chega | estorna com a mesma chave, grava o pagamento `REFUNDED` com motivo `PAID_AFTER_CANCELLATION` e **não publica evento nenhum** |
| **Orquestrador** (F7) | o Billing publicou `PaymentConfirmed` **antes** de o orçamento virar `CANCELED` | manda `RefundPayment` ao Billing, que responde `PaymentRefunded` |

A primeira camada não publica evento porque **não há passo pendente**: a saga já está `COMPENSATED` e um
`PaymentRefunded` avulso seria resposta para quem não perguntou. No lugar do evento ficam um **WARN com
`sagaId` e `correlationId`** e um contador — é o que o bloco de observabilidade transforma em alerta. Se o
estorno falhar, o webhook responde **5xx** e o provedor reentrega; esgotada a reentrega, o backstop é o
alerta.

A segunda camada continua necessária: ela cobre a corrida genuína, em que o Billing não tinha como saber. E
é por causa dela que a regra "publicar só se a transição mudou o estado" vale **apenas para o webhook** — um
`RefundPayment` que chega para um pagamento já estornado **ainda publica** `PaymentRefunded`, senão o
orquestrador nunca confirma o passo.

## Alternativas consideradas

### Checkout API (transparente) — descartada

| A favor | Contra (o que decidiu) |
|---|---|
| O cliente não sai da nossa página; mais controle sobre a UX | Exige **tokenizar o cartão no front-end** e, com isso, entrar no escopo de PCI-DSS |
| | Não há front-end neste trabalho: a entrega é API |
| | Mais superfície de segurança para um ganho que ninguém avalia aqui |

### Orders API — oferecida pelo provedor e não adotada

A documentação oferece a Orders API como caminho novo e unificado. Ficou de fora por três motivos: o fluxo de
Checkout Pro já resolve o problema com quatro chamadas estáveis; a Orders API acrescenta um conceito a
modelar (a *ordem*) que não tem contraparte no nosso domínio — a OS já é a ordem; e adotá-la obrigaria a
reescrever o adapter inteiro sem mudar um único requisito atendido.

### Simular o pagamento inteiro — descartada

Rápida e grátis, mas o enunciado pede integração com provedor externo. O que **ficou** da ideia: um adapter
fake nos perfis de desenvolvimento e teste, para a aplicação subir sem rede, e um servidor HTTP simulado nos
testes de contrato, que é onde os ramos que a sandbox não dá de graça (5xx, timeout, corpo malformado, erro
2018, falha no estorno) são exercidos.

## Consequências

**Positivas**

- Nenhum dado de cartão atravessa a nossa aplicação: o cartão é digitado na página do provedor.
- O webhook tem endereço fixo, configurado uma vez, que sobrevive a `destroy` e a *reset* do laboratório.
- A compensação é fechada: cobrança não paga se cancela, cobrança paga se estorna, e nenhuma das duas
  consegue travar a saga.
- O adapter é testável sem rede e sem conta: os quatro verbos são exercidos contra um servidor simulado.

**Negativas (aceitas e mitigadas)**

- **A confirmação depende de uma notificação externa chegar.** Mitigação: o prazo de pagamento é do
  orquestrador, não do provedor — vencido o prazo, a saga compensa sozinha, e um pagamento que chegue depois
  cai no caminho de estorno automático.
- **A proteção do webhook é só a assinatura.** A rota é pública por necessidade e não há *authorizer* no
  Gateway; a verificação é HMAC-SHA256 comparado em tempo constante, **negando quando o segredo não está
  configurado**. Em compensação, o endpoint não confia em nada do corpo recebido.
- ~~**O template do manifesto assinado é hipótese.**~~ **Deixou de ser: confirmado em 2026-10-29.** A
  página de webhooks do provedor mostra apenas o formato do header (`ts=…,v1=…`) e manda usar o SDK dele,
  então o template `id:<data.id>;request-id:<x-request-id>;ts:<ts>;` foi implementado como hipótese. A
  simulação do painel, **assinada pelo próprio provedor**, atravessou Gateway → ALB → pod e a verificação
  **aceitou**. Isso fecha as duas incertezas de uma vez: o template está correto **e** o API Gateway
  repassa o `x-request-id` intacto — se ele reescrevesse o header, a assinatura não fecharia.
- **A expiração curta é aceita pelo provedor — e honrada por ele.** A dúvida era se o provedor recusaria a
  janela curta da demonstração; medido em 2026-10-29: a preferência foi **criada sem reclamação** com a
  janela derivada do prazo de pagamento, e o formato de data do adapter (`+00:00`, não `Z`) foi aceito.
  Mais que aceita, a janela é **aplicada**: a primeira tentativa de pagamento, feita depois de a janela
  passar, foi recusada na página do provedor com *"O tempo para realizar esta operação expirou"*. Isso
  resolve a dúvida, mas cria uma operacional: **a janela tem de cobrir o tempo de um humano no navegador**.
  Na sessão o prazo foi alongado para 2 horas no ConfigMap do cluster; o repositório continua com o valor
  curto, porque em produção quem compensa é o *scheduler* do orquestrador, não a expiração do provedor.

### O que a sandbox de contas de teste não permite

Três limites foram medidos em 2026-10-29 e **nenhum deles é defeito do adapter**. Ficam registrados porque
são exatamente o tipo de coisa que, sem registro, volta como suspeita de bug na próxima sessão.

**1 · O estorno é recusado.** `POST /v1/payments/{id}/refunds` responde **401**:

```json
{"message":"Unauthorized use of live credentials","error":"unauthorized","status":401,
 "cause":[{"code":7,"description":"Unauthorized use of live credentials"}]}
```

O pagamento correspondente traz `live_mode: true`, embora o dono do token seja uma conta marcada
`test_user`. O mesmo token **cria preferência** e **lê** o pagamento, e até `GET /v1/payments/{id}/refunds`
responde **200** — só a chamada que movimenta dinheiro é barrada. É restrição de credencial da conta, não
do nosso caminho de código, que é exercido ponta a ponta contra servidor simulado (5 cenários, incluindo
falha no estorno).

De brinde, a recusa provou a compensação em produção **melhor do que o caminho feliz provaria**: o 401 virou
retentativa, a retentativa esgotou, a mensagem caiu na DLQ e a saga ficou visível em `COMPENSATING` com o
passo de estorno pendente — que é literalmente o que a matriz de compensação prescreve para falha de
estorno. Compensação é retentável, nunca desistível.

**2 · ~~O provedor não entrega notificação.~~ Era configuração nossa — corrigido.** Durante a sessão, zero
requisições chegaram ao webhook depois de um pagamento real aprovado, e isso foi registrado aqui como
limitação do ambiente. **Estava errado.** O painel do provedor mantém **duas configurações separadas** —
modo de teste e modo de produção —, cada uma com URL, eventos e **segredo próprios**. A de teste estava
completa; a de **produção estava sem URL nenhuma**. Pagamento feito por conta de teste chega com
`live_mode: true` e portanto notifica pela configuração de **produção**, que não tinha destino. Os
eventos estavam certos nas duas o tempo todo.

Duas lições de desenho ficam, e a segunda é a que importa:

1. Quando um provedor separa ambientes, **o segredo também é por ambiente** — a simulação em modo de teste
   devolvia 401 com o segredo de produção implantado, e o sintoma é idêntico ao de template errado.
2. **A confirmação não pode depender só do webhook.** Uma caixa de texto vazia num painel externo derrubou
   o canal inteiro sem nenhum erro em lugar nenhum. O backstop existe e funcionou — o prazo do
   orquestrador compensou sozinho —, mas um `GET /v1/payments/{id}` periódico para pagamentos em
   `PENDING` fecharia o buraco, e fica registrado como melhoria.

**3 · As duas pontas têm de ser contas de teste.** Pagar como visitante ou com conta real contra um
vendedor de teste é recusado com *"Uma das partes com as quais você está tentando efetuar o pagamento é de
teste."* E o acesso à conta de teste é pelo **apelido**, não pelo e-mail. Dos cartões de teste do provedor,
o Visa terminado em **5682** foi aceito e o Mastercard terminado em **6351** foi recusado com *"A transação
não aceita este meio de pagamento"* — o resultado depende do meio de pagamento habilitado na conta, não só
do nome do portador.

## Em produção, o que poderia mudar

Com domínio próprio, a URL do webhook deixaria de depender de um gateway mantido fora do ciclo de vida do
ambiente. O segredo do webhook e o token de acesso sairiam do Parameter Store para um cofre com rotação
automática, e a verificação da assinatura usaria o SDK oficial em vez de um manifesto montado à mão. O
desenho — redirecionamento, status lido do provedor, idempotência no estorno, cancelamento best-effort —
continuaria o mesmo.

## Evidências

| Data | Evidência |
|---|---|
| 2026-10-25 | 13 testes de contrato do adapter contra servidor HTTP simulado: preferência 201 com `Bearer`, `external_reference` e expiração; `GET` devolvendo o status real; `PUT` cancelando com 200; **erro 2018 → não-cancelável**; outra recusa → recusado; estorno com `X-Idempotency-Key`; **5xx retentado 3× e então exceção**; 4xx **não** retentado; corpo malformado; timeout |
| 2026-10-25 | Testes do caso de uso: pagamento `approved` em orçamento `CANCELED` **estorna e não publica** `PaymentConfirmed`; `CancelPayment` publica `PaymentCanceled` **mesmo quando o provedor recusa**; `RefundPayment` de pagamento já estornado **ainda publica** `PaymentRefunded` |
| 2026-10-25 | Reentrega do webhook não cria segunda linha em `payment_attempts` nem segundo evento — *check-then-insert* devolvendo booleano, com `UNIQUE(payment_id, mp_payment_id, status_detail)` no banco como rede de segurança |
| 2026-10-25 | Formato de data produzido pelo adapter medido: `2026-10-25T12:30:00.000+00:00` (offset UTC, não `Z`). **Se o provedor aceita essa forma é medição pendente da sessão com a sandbox** |
| 2026-10-25 | Credenciais publicadas como SecureString em `/fase4/mercadopago/*` e conferidas por hash contra os arquivos de origem; nenhum valor em repositório |
| 2026-10-29 | **Pagamento real aprovado no provedor, R$ 257,70**, com `external_reference` igual ao id do orçamento. A OS atravessou `RECEIVED → UNDER_DIAGNOSIS → PENDING_APPROVAL → APPROVED → IN_PROGRESS → COMPLETED` com o dinheiro de verdade no meio |
| 2026-10-29 | Formato de data do adapter (`+00:00`) **aceito** na criação da preferência; a expiração enviada é **honrada** pelo provedor — tentativa após a janela recusada com "O tempo para realizar esta operação expirou" |
| 2026-10-29 | Tentativa recusada: o pagamento **continuou** `PENDING` e duas entregas da mesma notificação geraram **uma** linha em `payment_attempts` e **um** `PaymentAttemptRejected` |
| 2026-10-29 | Verificação da assinatura provada nos dois sentidos com notificações assinadas por nós: manifesto correto → **200**, adulterado → **401** |
| 2026-10-29 | **Template do manifesto e repasse do `x-request-id` CONFIRMADOS** pela simulação do painel, assinada pelo próprio provedor: a requisição atravessou Gateway → ALB → pod e a assinatura **validou**. Discriminado antes por duas chamadas: manifesto **com** `request-id` passou da verificação, **sem** foi recusado — logo o header chega intacto |
| 2026-10-29 | Causa real das zero entregas medida: a configuração de **produção** do painel estava **sem URL**, e pagamento de conta de teste chega com `live_mode: true`, notificando por ela. Os três segredos conferidos por hash: o implantado é o de produção; o de teste é outro |
| 2026-10-29 | Defeito achado pela própria simulação: `GET /v1/payments/{id}` com id inexistente devolve 404 e o webhook respondia **500** — reentrega infinita, porque o provedor insiste até receber 2xx. Corrigido: 404 do provedor vira `UnknownPaymentException`, o webhook reconhece com 200 e registra WARN |
| 2026-10-29 | Estorno **recusado pela sandbox** com 401 `Unauthorized use of live credentials` (pagamento em `live_mode: true`, token de conta `test_user`); `GET` de estornos responde 200 com o mesmo token. A recusa provou a compensação: retentativa → DLQ → saga em `COMPENSATING` com o passo de estorno pendente |
| 2026-10-29 | **Dois** pagamentos reais confirmados (R$ 257,70 cada), com os ids do provedor gravados em `payments.mp_payment_id`. `payment_attempts` com **1** linha (`cc_rejected_insufficient_amount`), e o outbox dos dois serviços **drenado** (`sent_at is null` = 0) — nenhum evento preso |
| 2026-10-29 | **Prazo de pagamento vencido compensou sozinho:** cobrança não paga → *scheduler* do orquestrador estourou o prazo → orçamento e pagamento `CANCELED` com motivo `PAYMENT_EXPIRED`. É o cancelamento *best-effort* desta ADR exercitado no ramo em que o cliente **nunca abriu o link** — o caso comum, e o que não tem API de cancelamento no provedor |
