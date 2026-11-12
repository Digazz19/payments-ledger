package ledger.repository;

import static ledger.jooq.Tables.ACCOUNTS;
import static ledger.jooq.Tables.ACCOUNT_BALANCES;

import java.util.Optional;
import java.util.UUID;
import ledger.domain.Account;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class AccountRepository {

    private final DSLContext dsl;

    public AccountRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(Account account) {
        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);

            tx.insertInto(ACCOUNTS)
                .set(ACCOUNTS.ID, account.id())
                .set(ACCOUNTS.OWNER_NAME, account.ownerName())
                .set(ACCOUNTS.CURRENCY, account.currency())
                .set(ACCOUNTS.COUNTRY, account.country())
                .set(ACCOUNTS.TYPE, "CUSTOMER")
                .set(ACCOUNTS.CREATED_AT, account.createdAt())
                .execute();

            tx.insertInto(ACCOUNT_BALANCES)
                .set(ACCOUNT_BALANCES.ACCOUNT_ID, account.id())
                .execute();
        });
    }

    public Optional<Account> findById(UUID id) {
        return dsl.select(
                ACCOUNTS.ID,
                ACCOUNTS.OWNER_NAME,
                ACCOUNTS.CURRENCY,
                ACCOUNTS.COUNTRY,
                ACCOUNT_BALANCES.BALANCE_MINOR,
                ACCOUNTS.CREATED_AT)
            .from(ACCOUNTS)
            .join(ACCOUNT_BALANCES).on(ACCOUNT_BALANCES.ACCOUNT_ID.eq(ACCOUNTS.ID))
            .where(ACCOUNTS.ID.eq(id))
            .fetchOptional(row -> new Account(
                row.get(ACCOUNTS.ID),
                row.get(ACCOUNTS.OWNER_NAME),
                row.get(ACCOUNTS.CURRENCY),
                row.get(ACCOUNTS.COUNTRY),
                row.get(ACCOUNT_BALANCES.BALANCE_MINOR),
                row.get(ACCOUNTS.CREATED_AT)));
    }
}