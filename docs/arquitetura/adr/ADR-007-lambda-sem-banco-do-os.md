# ADR — A Lambda de autenticação não acessa o banco do OS Service

- **Status:** Aceita (2026-09-29, implementada no bloco do domínio do OS Service)
- **Decisores:** dono do projeto, com análise e medições do agente
- **Relacionados:** [componentes.md](../componentes.md) · [catálogo de mensagens §6](../catalogo-de-mensagens.md) ·
  [ADR-003 — borda privada](ADR-003-borda-privada.md) · [banco-de-dados.md](../banco-de-dados.md)

## Contexto

O cliente se autentica por CPF: uma função Lambda recebe o CPF, confirma que ele pertence a um cliente e assina
um JWT com `groups=["CUSTOMER"]`. Na fase anterior, essa função lia a tabela `customers` **direto no banco**,
com credencial própria — o banco era um só, compartilhado pelos módulos do monolito.

Na fase atual esse banco é **do OS Service**, e o enunciado destaca em amarelo: *nenhum serviço acessa o banco
de outro*. A função deixou de poder ler a tabela.

Uma restrição medida pesa aqui: pelo laboratório, **todos os pods herdam a mesma role** (ver
[ADR-002](ADR-002-credencial-dos-pods.md)), então o isolamento entre serviços não vem do IAM — vem de
convenção e de desenho. Furar a regra na autenticação seria furá-la no lugar mais visível do trabalho.

## Decisão

**A Lambda consulta um endpoint interno do OS Service**, `GET /internal/customers/{cpf}`, que devolve apenas
`{id, name}`. A função perde qualquer credencial de banco: suas únicas dependências passam a ser o Parameter
Store (chave RSA e credencial de serviço) e o ALB interno.

O endpoint é protegido em **duas camadas, as duas obrigatórias**:

1. **Fora da allowlist do API Gateway.** O Gateway publica uma lista explícita de rotas; `/internal/...` não
   está nela, e rota não declarada responde 404 no próprio Gateway.
2. **Autenticação de serviço na aplicação.** O endpoint exige um segredo compartilhado, publicado como
   SecureString pelo Terraform do OS Service e lido em runtime pela função. A comparação é em tempo constante,
   e o filtro **nega quando o segredo não está configurado** (*fail closed*), em vez de ficar aberto porque
   uma variável de ambiente faltou.

A segunda camada não é redundância: foi medida. Num spike de rotas do Gateway, `../` e as variantes codificadas
são normalizadas e barradas, **mas `..;/` é repassado literalmente ao backend**. Se o servidor de trás
resolvesse esse segmento, a allowlist seria contornada. A credencial de serviço fecha a porta
independentemente disso — e um JWT de ADMIN válido **não** substitui a credencial, o que está coberto por teste.

## Alternativas consideradas

### Manter a leitura direta no banco — descartada

| A favor | Contra (o que decidiu) |
|---|---|
| Nenhum trabalho; menor latência; sem dependência de o OS estar de pé | Fere a letra de um requisito que o enunciado destaca |
| | Acopla a função ao **schema** do OS: uma migration de `customers` quebraria a autenticação |
| | Espalha credencial de banco para fora do serviço que é dono dele |

### Declarar a Lambda parte do contexto do OS Service — descartada como solução, mantida como argumento de reserva

Defender em ADR que função e serviço são o mesmo *bounded context* e que o acesso é interno. Honesto, e teria
economizado trabalho — mas continuaria acoplando ao schema, e a leitura mais natural do requisito é a que o
avaliador fará.

### Cópia local dos clientes na função, alimentada por eventos — descartada

Tiraria a dependência em runtime, ao custo de um projeção a manter, de consistência eventual no **login** (um
cliente recém-cadastrado não entraria) e de mais escopo do que o problema pede.

## Consequências

**Positivas**

- Nenhum serviço lê o banco de outro, inclusive no caminho de autenticação.
- A função não conhece o schema do OS: o contrato é um JSON de dois campos.
- Uma credencial de banco a menos circulando, e uma dependência a menos na função (o driver de Postgres saiu).

**Negativas (aceitas e mitigadas)**

- **O `/auth` passa a depender do OS Service estar de pé.** Mitigação: timeout curto e resposta **503**
  distinta de erro interno, para o cliente saber que é indisponibilidade temporária; réplicas e HPA no OS.
- **Um salto de rede a mais no login.** Irrelevante no volume do projeto; a chamada fica dentro da VPC.
- **Mais um segredo para gerenciar.** Mitigação: ele nasce do Terraform (`random_password`), vive como
  SecureString e nunca aparece em log, em variável de ambiente da função (só o *nome* do parâmetro vai) nem em
  repositório.
- **O endpoint existe e é alcançável de dentro da VPC.** É o preço de não ter identidade por pod no
  laboratório; a credencial de serviço é o que o substitui.

## Em produção, o que poderia mudar

Com IRSA ou Pod Identity, a autorização sairia do segredo compartilhado para uma **identidade por chamador**, e
a chamada poderia ser assinada (SigV4) ou passar por um *service mesh* com mTLS, sem credencial para rotacionar.
O desenho — autenticação consultando o dono do dado por API, nunca pelo banco — continuaria o mesmo.

## Evidências

| Data | Evidência |
|---|---|
| 2026-10-01 | Spike de rotas do Gateway: `../`, `%2e%2e/` e `..%2f` → 404; **`customers/..;/internal/...` repassado literalmente** ao backend — o que torna a autenticação de serviço obrigatória |
| 2026-10-22 | `InternalCustomerControllerTest`: 200 com a credencial, **401 sem ela**, 401 com credencial errada, **401 com JWT de ADMIN válido e sem credencial**, 404 para CPF que não é cliente |
| 2026-10-22 | `tests/osService.test.mjs` (29 testes verdes no total): credencial no header, CPF codificado no caminho, 404 → cliente ausente, 401/5xx e rede fora do ar → indisponível |
| 2026-10-22 | `lambda/db.mjs` e a dependência de Postgres removidos do pacote da função |
