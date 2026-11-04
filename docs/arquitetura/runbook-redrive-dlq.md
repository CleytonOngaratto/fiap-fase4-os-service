# Runbook: DLQ da Saga

O que fazer quando uma mensagem da Saga para numa fila de descarte. Os comandos são para **PowerShell 5.1** com o AWS CLI
e o `kubectl` já autenticados.

## Quando usar

Toda mensagem que falha `maxReceiveCount` vezes vai para a DLQ da fila, e a saga dela fica parada, à vista, sem desistir.
Use este runbook quando uma DLQ tiver mais de zero mensagens ou quando uma saga estiver em `COMPENSATING` há mais tempo que
as retentativas explicam.

| DLQ | Fila de origem | Quem consome a origem | Tentativas · visibilidade |
|---|---|---|---|
| `execution-commands-dlq.fifo` | `execution-commands.fifo` | Execution | 3 · 30 s |
| `billing-commands-dlq.fifo` | `billing-commands.fifo` | Billing | 3 · 30 s |
| `os-saga-replies-dlq.fifo` | `os-saga-replies.fifo` | OS (orquestrador) | 3 · 30 s |

Enquanto uma mensagem é retentada, as de trás **no mesmo grupo FIFO** esperam: o grupo é a OS, então só aquela ordem de
serviço fica parada. Ao cair na DLQ, a mensagem libera o grupo e as de trás andam.

## 1. Identificar

```powershell
$dlq = aws sqs get-queue-url --queue-name billing-commands-dlq.fifo --query QueueUrl --output text
aws sqs get-queue-attributes --queue-url $dlq --attribute-names ApproximateNumberOfMessages
aws sqs receive-message --queue-url $dlq --max-number-of-messages 1 --visibility-timeout 0 --attribute-names All --output json
```

A visibilidade zero lê sem esconder a mensagem. Do corpo, anote:

| Campo do envelope | Para quê |
|---|---|
| `type` | Qual comando ou resposta falhou |
| `workOrderId` | Qual OS está parada |
| `sagaId` e `correlationId` | Como achar o erro nos logs |
| `messageId` | A chave da inbox: é por ele que uma reentrega é reconhecida |

Em seguida, procure o erro no serviço que consome a fila de origem:

```powershell
kubectl -n billing logs deployment/billing-service --since=1h | Select-String "<correlationId>"
```

## 2. Classificar

| O que o log diz | Classe | O que fazer |
|---|---|---|
| Serviço fora do ar, 5xx do provedor, timeout, banco indisponível | **Transitória** | Corrija a causa e reenvie (passo 3) |
| Credencial do laboratório expirada | **Transitória** | Renove a credencial e reenvie |
| `401 Unauthorized use of live credentials` no estorno | **Permanente na sandbox** do Mercado Pago ([ADR-008](adr/ADR-008-pagamento-mercado-pago.md)) | Não adianta reenviar: registre o envelope. Fora da sandbox a mesma mensagem seria transitória |
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
fila. A permissão vem da credencial em uso: `sqs:StartMessageMoveTask`, `sqs:ListMessageMoveTasks`,
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

O redrive pelo `start-message-move-task` na AWS real, sob as permissões do laboratório, será medido na primeira sessão
com a mensagem da F6.
