# 002: Idempotência de depósitos e transferências

## Contexto

Os clientes repetem pedidos quando a resposta se perde. Sem proteção,
uma repetição move o dinheiro duas vezes.

## Decisão

- POST /deposits e POST /transfers exigem o header Idempotency-Key.
- A chave é reservada numa transação curta, com INSERT ... ON CONFLICT DO NOTHING.
- A resposta e o estado DONE são gravados na mesma transação do movimento.
- Erros de negócio ficam guardados. Erros inesperados libertam a chave.
- Uma chave em IN_PROGRESS há mais de 60 segundos pode ser assumida
  por um pedido com o mesmo corpo.
- Cada dono recebe um lock_token. Só o dono atual consegue concluir a chave.

## Porque é seguro reprocessar

Se a chave ainda está em IN_PROGRESS, o movimento nunca foi confirmado,
porque ambos são gravados na mesma transação.

Se o dono original estiver só lento, e não morto, ao tentar concluir
descobre que perdeu o token. A transação dele é desfeita, e o dinheiro
só é movido uma vez.

## Custos

- Um cliente cujo pedido ficou preso espera até 60 segundos antes de poder repetir.
- As chaves ficam guardadas para sempre. Em produção, seria preciso apagá-las
  ao fim de algum tempo, por exemplo 24 horas.