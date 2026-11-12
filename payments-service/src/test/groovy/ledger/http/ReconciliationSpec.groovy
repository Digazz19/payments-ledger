   package ledger.http

   class ReconciliationSpec extends ApiSpec {
       def "um ledger com movimentos esta consistente"() {
           given:
           def a = newAccount(balance: 1000)
           def b = newAccount()
           client.post("/transfers", transfer(a, b, 300))

           expect:
           reconciliation.check().isEmpty()
           reconciliation.ledgerTotal() == 0
       }

       def "deteta um saldo alterado por fora do ledger"() {
           given:
           def id = newAccount(balance: 100)

           when:
           sql.executeUpdate(
               "UPDATE account_balances SET balance_minor = 99 WHERE account_id = ?::uuid",
               [id]
           )

           then:
           reconciliation.check()*.accountId*.toString() == [id]

           cleanup:
           sql.executeUpdate(
               "UPDATE account_balances SET balance_minor = 100 WHERE account_id = ?::uuid",
               [id]
           )
       }
   }