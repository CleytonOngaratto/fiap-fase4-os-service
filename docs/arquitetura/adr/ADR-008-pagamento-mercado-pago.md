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
- **O template do manifesto assinado é hipótese, não fato.** A página de webhooks do provedor mostra apenas
  o formato do header (`ts=…,v1=…`) e manda usar o SDK dele. O formato implementado está marcado como
  hipótese no código e será medido pelo botão "Simular" do painel **antes** do primeiro pagamento real,
  junto com a confirmação de que o API Gateway repassa o `x-request-id` intacto até o pod — se ele
  reescrever o header, todo webhook legítimo vira 401 e o sintoma é idêntico ao de segredo errado.
- **A janela mínima de expiração da preferência é desconhecida.** O guia recomenda prazos de dias, mas a
  recomendação é por causa de pagamento offline e Pix, que levam horas para acreditar. Se o provedor recusar
  a janela curta da demonstração, a preferência vai sem expiração e o prazo continua valendo pelo
  *scheduler* do orquestrador, que é quem compensa.

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
