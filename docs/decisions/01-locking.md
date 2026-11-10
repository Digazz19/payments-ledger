# 001: Locking nas transferências

## Contexto

O TransferService lê o saldo, decide se há dinheiro e só depois grava.
Entre a leitura e a escrita, outros pedidos podem ler o mesmo saldo.

## Problema observado

Com saldo de 100 e 200 transferências simultâneas de 1:

- Respostas: <cola aqui o resultado do println>
- O CHECK balance_not_negative impediu saldos negativos.
- Os pedidos recusados pela base de dados devolveram 500 em vez de 422.

## Decisão

A preencher na Tarefa 3.2.