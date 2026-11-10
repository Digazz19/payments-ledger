package ledger.repository;

import static ledger.jooq.Tables.ACCOUNT_BALANCES;
import static ledger.jooq.Tables.LEDGER_ENTRIES;
import static ledger.jooq.Tables.TRANSFERS;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import ledger.domain.Transfer;
import ledger.domain.TransferStatus;
import ledger.http.ApiException;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record3;
import org.jooq.impl.DSL;

public class TransferRepository {

    private static final Field<Long> VERSION = DSL.field(DSL.name("version"), Long.class);
    private static final int MAX_ATTEMPTS = 30;

    private static class OptimisticConflict extends RuntimeException {
    }

    private final DSLContext dsl;

    public TransferRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Transfer transfer(UUID id, UUID source, UUID target, long amountMinor, String currency) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return tryTransfer(id, source, target, amountMinor, currency);
            } catch (OptimisticConflict conflict) {
                sleepBeforeRetry(attempt);
            }
        }
        throw new ApiException(409, "CONCURRENT_UPDATE", "Too many concurrent updates, try again");
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

    private Transfer tryTransfer(UUID id, UUID source, UUID target, long amountMinor, String currency) {
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);

            Map<UUID, Record3<UUID, Long, Long>> rows = tx
                .select(ACCOUNT_BALANCES.ACCOUNT_ID, ACCOUNT_BALANCES.BALANCE_MINOR, VERSION)
                .from(ACCOUNT_BALANCES)
                .where(ACCOUNT_BALANCES.ACCOUNT_ID.in(source, target))
                .fetchMap(ACCOUNT_BALANCES.ACCOUNT_ID);

            boolean hasFunds = rows.get(source).value2() >= amountMinor;

            Transfer transfer = new Transfer(
                id,
                source,
                target,
                amountMinor,
                currency,
                hasFunds ? TransferStatus.COMPLETED : TransferStatus.REJECTED,
                hasFunds ? null : "INSUFFICIENT_FUNDS"
            );

            if (hasFunds) {
                List<UUID> ordered = source.compareTo(target) < 0
                    ? List.of(source, target)
                    : List.of(target, source);

                for (UUID accountId : ordered) {
                    long delta = accountId.equals(source) ? -amountMinor : amountMinor;
                    int updated = tx.update(ACCOUNT_BALANCES)
                        .set(ACCOUNT_BALANCES.BALANCE_MINOR, ACCOUNT_BALANCES.BALANCE_MINOR.plus(delta))
                        .set(VERSION, VERSION.plus(1))
                        .where(ACCOUNT_BALANCES.ACCOUNT_ID.eq(accountId))
                        .and(VERSION.eq(rows.get(accountId).value3()))
                        .execute();
                    if (updated == 0) {
                        throw new OptimisticConflict();
                    }
                }
            }

            insertTransfer(tx, transfer);
            if (hasFunds) {
                insertEntries(tx, transfer);
            }
            return transfer;
        });
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, 5L * attempt + 1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
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

    private void insertEntries(DSLContext tx, Transfer transfer) {
        tx.insertInto(
                LEDGER_ENTRIES,
                LEDGER_ENTRIES.TRANSFER_ID,
                LEDGER_ENTRIES.ACCOUNT_ID,
                LEDGER_ENTRIES.DIRECTION,
                LEDGER_ENTRIES.AMOUNT_MINOR)
            .values(transfer.id(), transfer.sourceAccountId(), "DEBIT", transfer.amountMinor())
            .values(transfer.id(), transfer.targetAccountId(), "CREDIT", transfer.amountMinor())
            .execute();
    }

    private void moveMoney(DSLContext tx, Transfer transfer) {
        insertEntries(tx, transfer);

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