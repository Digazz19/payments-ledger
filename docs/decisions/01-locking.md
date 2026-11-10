# 001: Locking nas transferências

## Contexto

O TransferService lia o saldo, decidia se havia dinheiro e só depois gravava.
Entre a leitura e a escrita, outros pedidos podiam ler o mesmo saldo.

## Problema observado

Com saldo de 100 e 200 transferências simultâneas de 1:

- Respostas: [201:100, 500:82, 422:18]
- O CHECK balance_not_negative impediu saldos negativos.
- Os pedidos recusados pela base de dados devolveram 500 em vez de 422.

## Decisão

As transferências bloqueiam as duas contas com SELECT ... FOR UPDATE,
ordenado por account_id, na mesma transação que lê o saldo e grava o movimento.

- O saldo é lido depois de obter o lock, por isso já não pode estar desatualizado.
- A ordem fixa dos locks evita deadlocks entre transferências opostas.
- Só as transferências que tocam nas mesmas contas esperam umas pelas outras.
- O pool do Hikari tem 10 ligações. Com locks na mesma conta, mais ligações
  não aumentam o débito e gastam memória no PostgreSQL.

## Resultado

- Antes: [201:100, 500:82, 422:18]
- Depois: [201:100, 422:100], estável em 10 execuções seguidas.

Os testes de concorrência usam no máximo 50 pedidos em paralelo. Com 1000
ligações simultâneas, o servidor HTTP recusava ligações no WSL, e o teste
passava a medir os limites do Jetty em vez da lógica de locking.

## Alternativa avaliada: locking otimista

Implementação no branch `experiment/optimistic-locking`: coluna `version` em
`account_balances`, `UPDATE ... WHERE version = ?` e até 30 tentativas com
espera aleatória.

| Cenário (1000 pedidos, 50 em paralelo) | Pessimista | Otimista |
| --- | --- | --- |
| Conta disputada | 3718 ms, 0 erros | 5868 ms, 15 a 19 respostas 409 |
| Contas independentes | 834 ms | 786 ms |

Medianas de 3 execuções, num Ryzen 7 5800H com Docker Desktop em WSL.
O cenário da conta disputada corre primeiro e inclui o aquecimento da JVM
nas duas versões.

- Com muita disputa, o otimista é cerca de 58% mais lento e devolve erros
  ao cliente quando esgota as tentativas.
- Com pouca disputa, a diferença está dentro do ruído das medições.
- O TransferLockingSpec falha na versão otimista: transferências opostas
  entre duas contas também são um caso de muita disputa.

## Decisão final

Mantém-se o locking pessimista. Num sistema de pagamentos há contas muito
disputadas, como as de comerciantes, e é aí que o otimista perde.

## Custos

Uma conta muito disputada processa uma transferência de cada vez: cerca de
270 por segundo nesta máquina. Se isso não chegasse, a solução seria dividir
o saldo dessa conta por várias linhas, e não trocar de tipo de locking.

## Quando mudaria de opção

- Se os conflitos fossem raros e as leituras muito mais frequentes do que as escritas.
- Se a operação durasse vários pedidos HTTP, como um formulário editado
  durante minutos. Não é possível manter um lock na base de dados entre pedidos,
  e aí a coluna `version` é a solução natural.