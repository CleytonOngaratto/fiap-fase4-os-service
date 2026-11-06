# Runbook: DLQ da Saga

O que fazer quando uma mensagem da Saga para numa fila de descarte. Os comandos são para **PowerShell 5.1** com o AWS CLI
e o `kubectl` já autenticados.

## Quando usar

Toda mensagem que falha `maxReceiveCount` vezes vai para a DLQ da fila, e a saga dela fica parada, à vista, sem desistir.
Use este runbook quando uma DLQ tiver mais de zero mensagens ou quando uma saga estiver em `COMPENSATING` há mais tempo que
as retentativas explicam.

O aviso chega pelo alerta **DLQ com mensagem** da política `Car Workshop — Fase 4`: ele abre quando `saga_dlq_messages` passa
de 0 por 2 minutos. Como a condição separa por fila, o alvo do incidente deve ser a fila (a confirmar na sessão do B8). A página **DLQ** do painel mostra a profundidade de cada fila e as linhas
`leaving it for the redrive policy` por serviço. O incidente fecha sozinho depois que a DLQ volta a 0. Se a métrica aparecer
como `NaN`, a leitura da fila falhou: o `WARN` está no log do serviço, e o alerta não abre por isso.

| DLQ | Fila de origem | Quem consome a origem | Tentativas · visibilidade |
|---|---|---|---|
| `execution-commands-dlq.fifo` | `execution-commands.fifo` | Execution | 3 · 30 s |
| `billing-commands-dlq.fifo` | `billing-commands.fifo` | Billing | 3 · 30 s |
| `os-saga-replies-dlq.fifo` | `os-saga-replies.fifo` | OS (orquestrador) | 3 · 30 s |

O grupo FIFO é a OS, então uma mensagem em retentativa só afeta aquela ordem de serviço. O bloqueio não é total: os
consumidores leem lotes de até 10 mensagens e seguem depois de uma falha, então o que veio no mesmo lote é processado mesmo
com a mensagem da frente falhando. Medido na F6: o `CancelBudget` concluiu enquanto o `RefundPayment` ainda estava na segunda
das 3 tentativas. Fora do lote, as de trás esperam a da frente sair da fila.

## 1. Identificar

```powershell
$dlq = aws sqs get-queue-url --queue-name billing-commands-dlq.fifo --query QueueUrl --output text
aws sqs get-queue-attributes --queue-url $dlq --attribute-names ApproximateNumberOfMessages
aws sqs receive-message --queue-url $dlq --max-number-of-messages 1 --visibility-timeout 0 --attribute-names All --output json
```

A visibilidade zero lê sem esconder a mensagem, mas cada leitura soma 1 ao `ApproximateReceiveCount`. Do corpo, anote:

| Campo do envelope | Para quê |
|---|---|
| `type` | Qual comando ou resposta falhou |
| `workOrderId` | Qual OS está parada |
| `sagaId` e `correlationId` | Como seguir a OS nos logs, até a falha |
| `messageId` | A chave da inbox: é por ele que uma reentrega é reconhecida |

Em seguida, procure o erro no serviço que consome a fila de origem:

```powershell
kubectl -n billing logs -l app=billing-service --tail=-1 --since=1h --prefix | Select-String "Command failed"
```

| Fila de origem | Namespace e rótulo | Texto da falha |
|---|---|---|
| `billing-commands.fifo` | `billing`, `app=billing-service` | `Command failed` |
| `execution-commands.fifo` | `execution`, `app=execution-service` | `Command failed` |
| `os-saga-replies.fifo` | `os`, `app=car-workshop-api` | `Saga reply failed` |

Duas armadilhas medidas:

- **Procure pelo rótulo, com `--tail=-1`.** Com duas réplicas, `deployment/billing-service` lê um pod só: deu 6 linhas da
  OS, contra 8 pelo rótulo. Sem `--tail=-1`, o rótulo traz só as últimas 10 linhas de cada pod.
- **Busque pelo `sagaId` do envelope, não pelo texto.** A linha da falha leva o contexto da mensagem: `correlationId`,
  `sagaId` e `branchId`, que no New Relic chegam como `context.correlationId`, `context.sagaId` e `context.branchId`. A
  causa vem em `error.message`, que no estorno cita o `paymentId` do envelope. Uma consulta traz a falha e o que veio antes
  dela, nos três serviços:

  ```sql
  SELECT timestamp, entity.name, level, message, error.message FROM Log WHERE context.sagaId = '<sagaId do envelope>' SINCE 1 day ago
  ```

## 2. Classificar

| O que o log diz | Classe | O que fazer |
|---|---|---|
| Serviço fora do ar, 5xx do provedor, timeout, banco indisponível | **Transitória** | Corrija a causa e reenvie (passo 3) |
| Credencial do laboratório expirada | **Transitória** | Renove a credencial e reenvie |
| `Mercado Pago answered HTTP 401 for /v1/payments/{id}/refunds` | **Permanente na sandbox** do Mercado Pago: a resposta dele é `Unauthorized use of live credentials` ([ADR-008](adr/ADR-008-pagamento-mercado-pago.md)) | Não adianta reenviar: registre o envelope. Fora da sandbox a mesma mensagem seria transitória |
| `No saga for Work Order`, JSON inválido, campo obrigatório ausente | **Permanente** | Não reenvie: a mensagem nunca vai dar certo. Investigue quem a produziu |

Na dúvida, trate como permanente. Reenviar uma mensagem que não tem como dar certo só gasta mais 3 tentativas e devolve o
mesmo resultado, com o grupo daquela OS parado por mais ~90 s.

## 3. Reenviar

Só depois de a causa ter sumido:

```powershell
$dlqArn = aws sqs get-queue-attributes --queue-url $dlq --attribute-names QueueArn --query Attributes.QueueArn --output text
aws sqs start-message-move-task --source-arn $dlqArn
aws sqs list-message-move-tasks --source-arn $dlqArn
```

Sem `--destination-arn`, as mensagens voltam para a fila de origem. O movimento é assíncrono e só um pode estar ativo por
fila. Logo depois do disparo, a tarefa pode aparecer `COMPLETED` com `ApproximateNumberOfMessagesMoved` em 0, e a leitura
seguinte traz o número certo: confira pela profundidade das duas filas, não por esse contador. A permissão vem da credencial em uso: `sqs:StartMessageMoveTask`, `sqs:ListMessageMoveTasks`,
`sqs:ReceiveMessage`, `sqs:DeleteMessage` e `sqs:GetQueueAttributes` na DLQ, e `sqs:SendMessage` na origem.

**Por que reenviar é seguro:** a mensagem redirecionada ganha um novo `MessageId` do SQS e pode se intercalar com as
mensagens novas da fila
([documentação da AWS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue-redrive.html)).
A deduplicação da Saga não depende disso: a inbox usa o `messageId` do **envelope**, que viaja no corpo. Se o efeito já
aconteceu, a reentrega é reconhecida e ignorada (linha F10 da [matriz](matriz-de-compensacao.md)), e no estorno a chave de
idempotência do pagamento impede o segundo estorno no provedor.

Tudo vai junto: o movimento não filtra nem altera mensagens. Se a DLQ tiver uma mensagem permanente ao lado de uma
transitória, a permanente volta a falhar e retorna à DLQ depois das 3 tentativas.

## Mensagem permanente

Não apague antes de transcrever o envelope para o registro do incidente. A saga dela continua em `COMPENSATING`, e o `CANCELED`
da OS só virá quando a confirmação chegar. No caso de um estorno que o provedor nunca vai aceitar, a saída é operacional:
resolver o dinheiro por fora, pelo painel do provedor, e só então decidir o que fazer com a saga. Não há atalho automático,
de propósito: a [matriz](matriz-de-compensacao.md) garante que compensação é retentável, nunca desistível.

## 4. Verificar

A DLQ tem de voltar a zero e a saga tem de chegar ao fim:

```powershell
aws sqs get-queue-attributes --queue-url $dlq --attribute-names ApproximateNumberOfMessages
```

O estado da saga está no banco do OS. Suba um pod transitório com `psql`, usando o Secret do banco, sem nunca ver a senha:

```powershell
@'
apiVersion: v1
kind: Pod
metadata:
  name: psql-runbook
  namespace: os
spec:
  restartPolicy: Never
  containers:
    - name: psql
      image: postgres:16
      command: ["sleep", "900"]
      envFrom:
        - secretRef:
            name: car-workshop-db
'@ | kubectl apply -f -
kubectl -n os wait --for=condition=Ready pod/psql-runbook --timeout=120s
kubectl -n os exec -it psql-runbook -- sh
```

Dentro do pod, no shell do Linux:

```sh
PGPASSWORD="$DB_PASSWORD" psql "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USERNAME sslmode=require" -c "select state, cancel_reason, pending_compensations from saga_instance where work_order_id = 123"
exit
```

Ao terminar:

```powershell
kubectl -n os delete pod psql-runbook
```

| Resultado esperado | Significado |
|---|---|
| `COMPENSATED` com `pending_compensations` vazio, e a OS `CANCELED` | Compensação concluída |
| `COMPENSATING` com um passo pendente | Ainda falta aquela confirmação: volte ao passo 1 |
| Saga em estado normal e a OS andando | A mensagem era do caminho feliz, e a saga retomou de onde parou |

## Evidências

Mecanismo verificado em 2026-11-03: com `maxReceiveCount = 3`, a resposta que o orquestrador não consegue processar vai
intacta para a DLQ no 4º recebimento, sem efeito na saga (F9), e a resposta repetida não tem efeito (F10), ambos em
[`resiliencia.feature`](../../src/test/resources/features/saga/resiliencia.feature), contra filas FIFO em contêiner.

Redrive medido em 2026-11-05 no SQS real, com as permissões do laboratório, na mensagem da F6 (OS 13, `RefundPayment`):

| Medida | Resultado |
|---|---|
| Permissão | `start-message-move-task` e `list-message-move-tasks` aceitos pela credencial do laboratório |
| Movimento | `COMPLETED`, 1 de 1 mensagem, em fila FIFO |
| Tempo | Disparo e 1ª retentativa às 01:15:17, depois 01:15:47 e 01:16:17 (UTC); de volta à DLQ em cerca de 90 s |
| `MessageId` do SQS | Mudou: `dd2f40f0-…` → `7f823f36-…` |
| `messageId` do envelope | Igual: `c98dd409-e609-48bd-9456-5bd3015aa751` |
| `MessageGroupId` | Igual: `13` |
| `ApproximateReceiveCount` | Recomeçou: 5 antes (3 tentativas + 2 leituras), 4 depois (3 tentativas + 1 leitura) |
| Efeito | 401 nas 3 tentativas; a saga seguiu em `COMPENSATING` com `REFUND_PAYMENT` pendente |

Como a mensagem é permanente na sandbox, ela ficou na DLQ, com o envelope transcrito:

```json
{"messageId":"c98dd409-e609-48bd-9456-5bd3015aa751","type":"RefundPayment","version":1,"occurredAt":"2026-11-05T01:10:48.106809Z","sagaId":"42291c06-75b1-4c40-b388-7da7f60fafd9","workOrderId":13,"branchId":"SP-01","correlationId":"EVIDENCIA-B7-41946206096-49","causationId":"8db50474-e418-4ce4-8a65-b1fb90cd5c76","payload":{"paymentId":"186775499739","reason":"REPAIR_FAILED"}}
```
