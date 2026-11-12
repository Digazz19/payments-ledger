# 003: Revisão manual de transferências

## Contexto

O serviço de fraude pode pôr uma transferência em `IN_REVIEW`, por causa de uma
regra `REVIEW` ou porque não respondeu a tempo. Sem uma forma de decidir estas
transferências, ficariam paradas para sempre, sem mover dinheiro.

## Decisão

- `POST /transfers/{id}/review` aceita `{"decision": "APPROVE"}` ou `{"decision": "REJECT"}`.
- Só transferências em `IN_REVIEW` podem ser revistas, e só uma vez.
- A linha da transferência é bloqueada com `SELECT ... FOR UPDATE` antes de ver o estado.
- Ao aprovar, as duas contas são bloqueadas por ordem e o saldo é verificado de novo.
- Ordem dos locks: primeiro a transferência, depois as contas.
- O ledger continua a receber apenas inserções. Muda só o estado da linha em `transfers`.

| Situação | Resultado | HTTP |
| --- | --- | --- |
| Aprovar, com saldo | `COMPLETED`, o dinheiro move-se | 200 |
| Aprovar, sem saldo | `REJECTED`, `INSUFFICIENT_FUNDS` | 422 |
| Rejeitar | `REJECTED`, `REJECTED_BY_REVIEWER` | 200 |
| Já decidida | Nada muda, `TRANSFER_NOT_IN_REVIEW` | 409 |
| Não existe | `TRANSFER_NOT_FOUND` | 404 |

## Porquê

**Bloquear a transferência antes de ver o estado.** Dois revisores podem aprovar
ao mesmo tempo. Com o lock, o segundo pedido espera, lê o estado já atualizado e
recebe 409. Sem ele, os dois veriam `IN_REVIEW` e o dinheiro mover-se-ia duas vezes.

**Verificar o saldo de novo.** Entre a criação e a revisão, a conta pode ter gasto
o dinheiro noutras transferências. A aprovação é tratada como uma transferência nova,
com as contas bloqueadas por ordem.

**Primeiro a transferência, depois as contas.** Uma transferência nova bloqueia só
as contas e insere uma linha nova em `transfers`, por isso nunca espera pela linha
de uma transferência existente. Com esta ordem, não há ciclos de espera nem deadlocks.

## Resultados

- Teste automático: 20 aprovações em simultâneo dão 1 × 200 e 19 × 409, e o saldo
  só diminui uma vez.
- Teste manual com os dois serviços a correr: 10 aprovações em paralelo deram
  1 × 200 e 9 × 409.

## Custos e compromissos

- O repositório lança `ApiException`, que pertence à camada HTTP. Seria mais limpo
  usar exceções do domínio e traduzi-las no `App`.
- A revisão não tem chave de idempotência. Não é necessária, porque a máquina de
  estados já garante que cada transferência só é decidida uma vez.

## Fora do âmbito

- Autenticação e autorização de revisores: qualquer pessoa com acesso à API pode decidir.
- Registo de quem decidiu e quando.
- Prazo para a revisão: uma transferência pode ficar em `IN_REVIEW` indefinidamente.
- Notificar o cliente da decisão.