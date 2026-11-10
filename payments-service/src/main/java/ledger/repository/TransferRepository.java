package ledger.repository;

import static ledger.jooq.Tables.ACCOUNT_BALANCES;
import static ledger.jooq.Tables.LEDGER_ENTRIES;
import static ledger.jooq.Tables.TRANSFERS;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ledger.domain.Transfer;
import ledger.domain.TransferStatus;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class TransferRepository {

    private final DSLContext dsl;

    public TransferRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Transfer transfer(UUID id, UUID source, UUID target, long amountMinor, String currency) {
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);

            Map<UUID, Long> balances = tx
                .select(ACCOUNT_BALANCES.ACCOUNT_ID, ACCOUNT_BALANCES.BALANCE_MINOR)
                .from(ACCOUNT_BALANCES)
                .where(ACCOUNT_BALANCES.ACCOUNT_ID.in(source, target))
                .orderBy(ACCOUNT_BALANCES.ACCOUNT_ID)
                .forUpdate()
                .fetchMap(ACCOUNT_BALANCES.ACCOUNT_ID, ACCOUNT_BALANCES.BALANCE_MINOR);

            boolean hasFunds = balances.get(source) >= amountMinor;

            Transfer transfer = new Transfer(
                id,
                source,
                target,
                amountMinor,
                currency,
                hasFunds ? TransferStatus.COMPLETED : TransferStatus.REJECTED,
                hasFunds ? null : "INSUFFICIENT_FUNDS"
            );

            insertTransfer(tx, transfer);
            if (hasFunds) {
                moveMoney(tx, transfer);
            }
            return transfer;
        });
    }

    public void save(Transfer transfer) {
        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            insertTransfer(tx, transfer);
            if (transfer.status() == TransferStatus.COMPLETED) {
                moveMoney(tx, transfer);
            }
        });
    }

    public Optional<Transfer> findById(UUID id) {
        return dsl.selectFrom(TRANSFERS)
            .where(TRANSFERS.ID.eq(id))
            .fetchOptional(row -> new Transfer(
                row.getId(),
                row.getSourceAccountId(),
                row.getTargetAccountId(),
                row.getAmountMinor(),
                row.getCurrency(),
                TransferStatus.valueOf(row.getStatus()),
                row.getReason()));
    }

    private void insertTransfer(DSLContext tx, Transfer transfer) {
        tx.insertInto(TRANSFERS)
            .set(TRANSFERS.ID, transfer.id())
            .set(TRANSFERS.SOURCE_ACCOUNT_ID, transfer.sourceAccountId())
            .set(TRANSFERS.TARGET_ACCOUNT_ID, transfer.targetAccountId())
            .set(TRANSFERS.AMOUNT_MINOR, transfer.amountMinor())
            .set(TRANSFERS.CURRENCY, transfer.currency())
            .set(TRANSFERS.STATUS, transfer.status().name())
            .set(TRANSFERS.REASON, transfer.reason())
            .execute();
    }

    private void moveMoney(DSLContext tx, Transfer transfer) {
        tx.insertInto(
                LEDGER_ENTRIES,
                LEDGER_ENTRIES.TRANSFER_ID,
                LEDGER_ENTRIES.ACCOUNT_ID,
                LEDGER_ENTRIES.DIRECTION,
                LEDGER_ENTRIES.AMOUNT_MINOR)
            .values(transfer.id(), transfer.sourceAccountId(), "DEBIT", transfer.amountMinor())
            .values(transfer.id(), transfer.targetAccountId(), "CREDIT", transfer.amountMinor())
            .execute();

        tx.update(ACCOUNT_BALANCES)
            .set(ACCOUNT_BALANCES.BALANCE_MINOR, ACCOUNT_BALANCES.BALANCE_MINOR.minus(transfer.amountMinor()))
            .where(ACCOUNT_BALANCES.ACCOUNT_ID.eq(transfer.sourceAccountId()))
            .execute();

        tx.update(ACCOUNT_BALANCES)
            .set(ACCOUNT_BALANCES.BALANCE_MINOR, ACCOUNT_BALANCES.BALANCE_MINOR.plus(transfer.amountMinor()))
            .where(ACCOUNT_BALANCES.ACCOUNT_ID.eq(transfer.targetAccountId()))
            .execute();
    }
}