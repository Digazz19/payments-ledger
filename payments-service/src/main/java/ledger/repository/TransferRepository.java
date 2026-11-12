package ledger.repository;

import static ledger.jooq.Tables.ACCOUNT_BALANCES;
import static ledger.jooq.Tables.LEDGER_ENTRIES;
import static ledger.jooq.Tables.TRANSFERS;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import ledger.domain.Transfer;
import ledger.domain.TransferStatus;
import ledger.http.ApiException;
import ledger.jooq.tables.records.TransfersRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class TransferRepository {

    private final DSLContext dsl;

    public TransferRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Transfer transfer(
            UUID id,
            UUID source,
            UUID target,
            long amountMinor,
            String currency,
            BiConsumer<DSLContext, Transfer> beforeCommit) {
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);

            Map<UUID, Long> balances = lockBalances(tx, source, target);
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
            beforeCommit.accept(tx, transfer);
            return transfer;
        });
    }

    public void save(Transfer transfer, BiConsumer<DSLContext, Transfer> beforeCommit) {
        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            insertTransfer(tx, transfer);
            if (transfer.status() == TransferStatus.COMPLETED) {
                moveMoney(tx, transfer);
            }
            beforeCommit.accept(tx, transfer);
        });
    }

    public Transfer review(UUID id, boolean approve) {
        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);

            TransfersRecord row = tx.selectFrom(TRANSFERS)
                .where(TRANSFERS.ID.eq(id))
                .forUpdate()
                .fetchOne();

            if (row == null) {
                throw new ApiException(404, "TRANSFER_NOT_FOUND", "Transfer not found");
            }

            Transfer current = toTransfer(row);
            if (current.status() != TransferStatus.IN_REVIEW) {
                throw new ApiException(409, "TRANSFER_NOT_IN_REVIEW", "Transfer was already decided");
            }

            Transfer decided;
            if (!approve) {
                decided = withStatus(current, TransferStatus.REJECTED, "REJECTED_BY_REVIEWER");
            } else {
                Map<UUID, Long> balances = lockBalances(tx, current.sourceAccountId(), current.targetAccountId());
                boolean hasFunds = balances.get(current.sourceAccountId()) >= current.amountMinor();
                decided = hasFunds
                    ? withStatus(current, TransferStatus.COMPLETED, null)
                    : withStatus(current, TransferStatus.REJECTED, "INSUFFICIENT_FUNDS");
                if (hasFunds) {
                    moveMoney(tx, decided);
                }
            }

            tx.update(TRANSFERS)
                .set(TRANSFERS.STATUS, decided.status().name())
                .set(TRANSFERS.REASON, decided.reason())
                .where(TRANSFERS.ID.eq(id))
                .execute();

            return decided;
        });
    }

    public Optional<Transfer> findById(UUID id) {
        return dsl.selectFrom(TRANSFERS)
            .where(TRANSFERS.ID.eq(id))
            .fetchOptional(this::toTransfer);
    }

    private Map<UUID, Long> lockBalances(DSLContext tx, UUID source, UUID target) {
        return tx
            .select(ACCOUNT_BALANCES.ACCOUNT_ID, ACCOUNT_BALANCES.BALANCE_MINOR)
            .from(ACCOUNT_BALANCES)
            .where(ACCOUNT_BALANCES.ACCOUNT_ID.in(source, target))
            .orderBy(ACCOUNT_BALANCES.ACCOUNT_ID)
            .forUpdate()
            .fetchMap(ACCOUNT_BALANCES.ACCOUNT_ID, ACCOUNT_BALANCES.BALANCE_MINOR);
    }

    private Transfer toTransfer(TransfersRecord row) {
        return new Transfer(
            row.getId(),
            row.getSourceAccountId(),
            row.getTargetAccountId(),
            row.getAmountMinor(),
            row.getCurrency(),
            TransferStatus.valueOf(row.getStatus()),
            row.getReason());
    }

    private Transfer withStatus(Transfer transfer, TransferStatus status, String reason) {
        return new Transfer(
            transfer.id(),
            transfer.sourceAccountId(),
            transfer.targetAccountId(),
            transfer.amountMinor(),
            transfer.currency(),
            status,
            reason);
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