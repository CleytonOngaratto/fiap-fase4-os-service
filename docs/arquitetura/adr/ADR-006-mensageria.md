# ADR — mensageria: SNS FIFO publicando em SQS FIFO, com outbox transacional

> Escrito em 2026-10-18, no bloco do esqueleto andante, com as medições na nuvem de 2026-09-29 e os testes
> automatizados com fila real do mesmo bloco.

- **Status:** Aceita (2026-09-29)
- **Decisores:** dono do projeto, com análise e medições do agente
- **Relacionados:** [ADR-001 — Saga orquestrada](ADR-001-saga-orquestrada.md) ·
  [ADR-005 — divisão em serviços](ADR-005-divisao-em-servicos.md) ·
  [catálogo de mensagens](../catalogo-de-mensagens.md) ·
  [matriz de compensação](../matriz-de-compensacao.md)

## Contexto

A Saga precisa de comunicação assíncrona entre três serviços que não compartilham banco. Os requisitos vêm do
próprio fluxo:

1. **Ordem importa por ordem de serviço.** `PaymentConfirmed` chegando antes de `PaymentRequested`, ou um
   estorno antes da cobrança, corromperia o estado da saga.
2. **Participante fora do ar não pode perder mensagem** — a mensagem espera na fila e é processada quando o
   serviço volta.
3. **Mensagem venenosa precisa de saída**, com alerta e caminho de reprocessamento.
4. **Gravar o estado e publicar o evento têm de ser atômicos.** Gravar e depois publicar em dois passos é o
   *dual write*: uma queda no meio perde o evento ou publica um evento fantasma.

O ambiente é o AWS Academy, com orçamento fechado e um cluster pequeno.

## Decisão

**Tópicos SNS FIFO publicando em filas SQS FIFO**, com `MessageGroupId` = identificador da ordem de serviço, e
**outbox transacional** em quem publica.

| Peça | Como fica | Por quê |
|---|---|---|
| **Comando** (orquestrador → participante) | vai **direto para a fila** do participante | o destinatário é um só; tópico aqui não acrescentaria nada |
| **Evento** (participante → orquestrador) | publicado no **tópico do participante**, entregue na fila de respostas por assinatura com *raw delivery* | o fato é público: um consumidor novo assina sem o publicador saber dele |
| **Ordem** | `MessageGroupId` = identificador da ordem de serviço | ordem garantida **por ordem de serviço**, com paralelismo entre ordens diferentes |
| **Deduplicação** | explícita, pelo identificador da mensagem; nunca por conteúdo | duas mensagens legítimas podem ter o mesmo corpo; o identificador é gerado no outbox e é único |
| **Falha** | fila de descarte em cada fila, com poucas tentativas e *visibility timeout* curto | uma mensagem venenosa trava **apenas aquela ordem de serviço**, e por pouco tempo, antes de sair do caminho |
| **Atomicidade** | **outbox** na mesma transação do estado, e um *relay* que publica depois | nunca existe "estado salvo sem evento" nem "evento sem estado" |

Dois detalhes que o desenho exige e que não são opcionais:

- **O consumidor é idempotente.** FIFO reduz duplicata, não a elimina: a janela de deduplicação é de minutos, e
  a entrega continua sendo "ao menos uma vez". Cada consumidor decide pelo **estado**: receber a mesma resposta
  duas vezes é um não-evento, não um erro — tratá-la como erro mandaria a duplicata para a fila de descarte.
- **O relay pode republicar.** Se a publicação funciona e a transação do relay falha depois, a mensagem sai de
  novo; a deduplicação pelo identificador da mensagem absorve isso dentro da janela, e a idempotência do
  consumidor absorve fora dela.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| **SQS Standard** | sem ordem; o fluxo tem pares em que a ordem muda o resultado (cobrança e estorno). Exigiria reordenar no consumidor, reinventando mal o que a FIFO já faz |
| **Kafka gerenciado** | o custo por hora não cabe no orçamento do laboratório |
| **Kafka ou RabbitMQ no cluster** | não cabe em memória ao lado de três aplicações Java com agente de observabilidade, e passaria a ser mais um componente para operar e monitorar |
| **EventBridge** | ótimo para roteamento por conteúdo, mas sem garantia de ordem por chave, que é o requisito principal |
| **Chamadas REST diretas entre os serviços** | acopla a disponibilidade: um participante fora do ar derruba o fluxo, e cada retentativa viraria código nosso |
| **Publicar direto do código, sem outbox** | *dual write*: perde evento ou publica evento fantasma em qualquer queda entre o commit e a publicação |
| **Escolhida:** SNS FIFO → SQS FIFO com outbox | ordem por ordem de serviço, retentativa e descarte nativos, custo praticamente zero no volume do projeto, e atomicidade resolvida no banco local |

## Consequências

**Positivas**

- Ordem garantida onde importa, com paralelismo entre ordens de serviço diferentes.
- Participante fora do ar **pausa** o fluxo em vez de quebrá-lo; ao voltar, a fila drena.
- Falha repetida tem destino visível, com alerta e reprocessamento.
- "Salvar e publicar" é atômico, com o banco local como única fonte da verdade.
- Custo por uso, sem nada para operar.

**Negativas (aceitas e mitigadas)**

- **Bloqueio de cabeça de fila:** enquanto uma mensagem de um grupo não é apagada, as seguintes **do mesmo
  grupo** esperam. É o comportamento desejado numa saga — mas obriga tentativas e *visibility timeout*
  pequenos, para que uma mensagem travada não segure a ordem de serviço por muito tempo.
- **Idempotência é obrigação do consumidor**, não um presente da fila.
- **Limite de dez atributos por mensagem.** O envelope usa quatro; os de rastreamento distribuído somam mais
  três. Fica pouca folga, e isso é registrado para quando a observabilidade distribuída crescer.
- **O relay é mais uma peça para operar:** se ele parar, nada é publicado, embora nada se perca. A fila de
  pendentes do outbox é um sinal direto disso.
- **Simplificação assumida no esqueleto:** o Execution publica **direto no tópico**, sem outbox, porque o
  outbox dele depende do banco que entra no bloco seguinte. Enquanto isso, a reentrega do comando é absorvida
  por uma deduplicação **determinística**, derivada do próprio comando — o mesmo comando sempre produz o mesmo
  identificador de evento. É uma dívida registrada, não um descuido.

## Em produção, o que poderia mudar

A topologia ficaria igual. Mudariam as bordas: alarmes de idade da mensagem e de profundidade da fila de
descarte na própria nuvem, criptografia com chave própria, e identidade por serviço, de forma que cada
aplicação só alcance as suas filas e tópicos. Com volume maior, valeria avaliar o particionamento por grupo
para aumentar o paralelismo sem perder a ordem por ordem de serviço.

## Evidências

| Data | Evidência |
|---|---|
| 2026-09-29 | Na nuvem, com recursos reais: tópico FIFO publicando em fila FIFO com *raw delivery* manteve a **ordem por grupo**; uma mensagem repetida com o mesmo identificador de deduplicação **não foi entregue** e devolveu o mesmo identificador de mensagem; os atributos chegaram intactos |
| 2026-09-29 | Descarte provado: com poucas tentativas configuradas, mensagens recebidas e não apagadas foram para a fila de descarte **na ordem**, e a fila principal ficou vazia |
| 2026-10-11 | Uma aplicação **sem nenhuma credencial configurada** publicou no tópico FIFO e a mensagem chegou crua na fila assinada — a identidade vem do nó (ver [ADR-002](ADR-002-credencial-dos-pods.md)) |
| 2026-10-18 | Infraestrutura das filas em código: [`terraform/sqs.tf` do Execution](https://github.com/CleytonOngaratto/fiap-fase4-execution-service/blob/main/terraform/sqs.tf) (fila de comandos e descarte) e [`terraform/sqs.tf` do OS](https://github.com/CleytonOngaratto/fiap-fase4-os-service/blob/main/terraform/sqs.tf) (fila de respostas, política que libera **apenas** os dois tópicos e as assinaturas com *raw delivery*) |
| 2026-10-18 | Outbox atômico: a linha do outbox só é gravada **dentro** da transação da abertura — o adaptador exige transação ativa e falha alto sem ela, provado em [`SagaOutboxGatewayTest`](https://github.com/CleytonOngaratto/fiap-fase4-os-service/blob/main/src/test/java/br/com/fiap/postech/carworkshop/workorder/adapter/gateway/SagaOutboxGatewayTest.java) |
| 2026-10-18 | Caminho completo em teste automatizado contra fila real (emulador compatível com a API de filas): abrir a ordem grava o comando com o identificador de correlação da requisição, o relay o publica com os quatro atributos do envelope e marca a linha como enviada, e a resposta do participante avança o status. A **mesma resposta entregue duas vezes** avança o status uma vez só e não deixa nada para o descarte ([`SagaMessagingIntegrationTest`](https://github.com/CleytonOngaratto/fiap-fase4-os-service/blob/main/src/test/java/br/com/fiap/postech/carworkshop/workorder/infrastructure/messaging/SagaMessagingIntegrationTest.java)) |
| 2026-10-18 | Deduplicação determinística do lado do participante: o mesmo comando reentregue produz o **mesmo** identificador de evento, provado em [`DiagnosisStartedEventTest`](https://github.com/CleytonOngaratto/fiap-fase4-execution-service/blob/main/src/test/java/br/com/fiap/postech/carworkshop/execution/domain/message/DiagnosisStartedEventTest.java) e no consumidor com fila real |
