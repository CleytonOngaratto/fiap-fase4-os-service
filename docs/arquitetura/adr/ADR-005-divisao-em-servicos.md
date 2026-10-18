# ADR — por onde o monolito foi cortado em três serviços

> Escrito em 2026-10-18, no bloco do esqueleto andante, a partir da leitura do monolito da Fase 3 e do escopo
> exigido no Tech Challenge.

- **Status:** Aceita (2026-09-24)
- **Decisores:** dono do projeto, com análise do agente
- **Relacionados:** [componentes.md](../componentes.md) · [ADR-001 — Saga orquestrada](ADR-001-saga-orquestrada.md) ·
  [catálogo de mensagens](../catalogo-de-mensagens.md) · [máquina de estados](../maquina-de-estados.md)

## Contexto

A Fase 3 entregou um **monolito modular** em Quarkus, com Clean Architecture e módulos já separados por pasta
(`customer`, `vehicle`, `autoservice`, `inventory`, `auth`, `workorder`), comunicando-se em memória por portas.
O enunciado da Fase 4 exige **três ou mais microsserviços independentes**, cada um com repositório,
infraestrutura e **banco próprio**, e destaca que **nenhum serviço acessa o banco de outro**.

A pergunta não era *se* dividir, e sim **por onde cortar**. Um corte ruim produz serviços que não conseguem
decidir nada sozinhos e passam a vida conversando — o acoplamento sai do código e entra na rede, onde é pior.

O enunciado também nomeia, ele mesmo, três responsabilidades: a **ordem de serviço** (abertura, status,
histórico), o **orçamento e o pagamento** e a **execução** (fila, diagnóstico, reparo, finalização comunicada
ao serviço de ordens).

## Decisão

Cortar por **responsabilidade de negócio e dono do dado**, seguindo as três responsabilidades do enunciado:

| Serviço | Decide sobre | Herança da Fase 3 |
|---|---|---|
| **OS Service** | a ordem de serviço e seu status, os cadastros (clientes, veículos, catálogo, estoque), a autenticação do atendimento e o **estado da Saga** | herda o monolito, menos o que saiu |
| **Billing Service** | o orçamento, a aprovação do cliente, a cobrança e o estorno | nasce enxuto; recebe o que precisa por evento |
| **Execution Service** | a fila de execução por filial, o diagnóstico e o reparo | nasce enxuto; recebe o que precisa por comando |

Três consequências de desenho que vêm juntas com o corte:

1. **O dono do dado é quem responde por ele.** O catálogo de serviços e peças fica no OS porque é lá que ele é
   mantido; o Execution, que precisa validar os itens de um diagnóstico, **pergunta ao OS** em vez de guardar
   uma cópia.
2. **Os eventos carregam estado.** O Billing não consulta o OS a cada passo para montar um orçamento: o evento
   que o aciona traz o retrato dos itens e dos preços já congelados. Isso mantém o Billing capaz de trabalhar
   com o OS fora do ar.
3. **O orquestrador mora no OS.** A ordem de serviço é a entidade em volta da qual tudo gira, e o status dela é
   o que o cliente enxerga — o dono do fluxo é o dono dessa entidade. O pacote da Saga fica isolado, com regra
   própria de arquitetura, para que essa responsabilidade extra não se espalhe pelo serviço.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| **Corte por camada** (um serviço de API, um de regras, um de dados) | o *distributed monolith* clássico: nenhuma mudança de negócio caberia num serviço só, e todo deploy seria coordenado |
| **Corte por entidade** (um serviço por tabela: clientes, veículos, peças…) | serviços anêmicos de CRUD; abrir uma ordem exigiria orquestrar cinco chamadas síncronas, e a consistência viraria problema de quem chama |
| **Dois serviços** (ordens + um "resto") | atende o mínimo do enunciado pela metade e junta pagamento com execução, que têm donos, prazos e falhas completamente diferentes |
| **Cinco serviços** (separando cadastros e estoque do OS) | cadastros e estoque mudam **com** a ordem de serviço e participam das mesmas transações locais (reservar e baixar peças); separá-los transformaria uma transação local em mais uma saga, sem ganho |
| **Escolhida:** três serviços por responsabilidade de negócio | cada um decide sozinho o que é seu, e as três responsabilidades que o enunciado nomeia ficam explícitas na arquitetura |

## Consequências

**Positivas**

- Cada serviço tem **um motivo para mudar**: preço e pagamento não mexem no Execution; fila de oficina não mexe
  no Billing.
- O banco por serviço é consequência natural do corte, não uma imposição: ninguém precisa do banco do outro.
- A fronteira é **testável**: o contrato é o envelope das mensagens, e cada serviço é verificável sozinho.
- O deploy é independente por serviço, com pipeline próprio.

**Negativas (aceitas e mitigadas)**

- **O OS acumula responsabilidade** (ordens, cadastros, catálogo, estoque e o orquestrador). Mitigação: pacote
  da Saga isolado, com regra de arquitetura que impede o domínio e os casos de uso de conhecerem
  infraestrutura, e cobertura mínima verificada no pipeline.
- **A consistência entre serviços passa a ser eventual.** Mitigação: a Saga com compensação em cada etapa, e o
  status que o cliente vê sempre projetado a partir do estado do OS.
- **Uma dependência síncrona sobrou** (Execution → catálogo do OS). Mitigação: justificada no catálogo de
  mensagens, com timeout curto e resposta clara ao mecânico quando o OS não responde.
- **Cadastro duplicado de conhecimento no envelope:** o retrato dos itens viaja nas mensagens. É o preço de não
  consultar o OS a cada passo, e é o que mantém o preço congelado no momento do orçamento.

## Em produção, o que poderia mudar

Nada no corte em si. O que mudaria é o **isolamento**: no laboratório, todos os pods compartilham a mesma
identidade de nuvem, então o isolamento entre os bancos é por convenção e por *namespace*, não imposto pela
plataforma. Com identidade por serviço, cada um alcançaria apenas os seus próprios segredos, filas e tabelas —
e a regra "nenhum serviço acessa o banco de outro" passaria a ser garantida pela infraestrutura, não só pelo
código. O registro dessa limitação está no [ADR-002](ADR-002-credencial-dos-pods.md).

## Evidências

| Data | Evidência |
|---|---|
| 2026-09-24 | Leitura do monolito da Fase 3: o diagnóstico e a geração do orçamento aconteciam **na mesma chamada**, e a baixa de estoque na mesma transação da aprovação — os dois pontos que o corte separa em etapas ligadas por evento |
| 2026-10-01 | [catálogo de mensagens §6](../catalogo-de-mensagens.md): cada chamada síncrona que sobrou, com o motivo explícito e as proteções |
| 2026-10-14 | Os três serviços subiram pelo **próprio pipeline** e responderam **pelo Gateway**, cada um no seu *namespace*, com os segredos de cada um visíveis apenas no *namespace* dele |
| 2026-10-18 | O primeiro fluxo que atravessa a fronteira sem banco compartilhado: abrir uma ordem grava o comando no outbox do OS, o Execution o consome e responde com um evento, e o OS avança o status — provado em teste automatizado com fila real ([`SagaMessagingIntegrationTest`](https://github.com/CleytonOngaratto/fiap-fase4-os-service/blob/main/src/test/java/br/com/fiap/postech/carworkshop/workorder/infrastructure/messaging/SagaMessagingIntegrationTest.java)) |
